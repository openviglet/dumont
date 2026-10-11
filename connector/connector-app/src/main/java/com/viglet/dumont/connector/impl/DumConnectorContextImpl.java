/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.impl;

import static com.viglet.turing.client.sn.TurSNConstants.ID_ATTR;
import static com.viglet.turing.client.sn.TurSNConstants.SOURCE_APPS_ATTR;
import static com.viglet.turing.client.sn.job.TurSNJobAction.DELETE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.batch.JobItemBatchProcessor;
import com.viglet.dumont.connector.chain.JobProcessingChain;
import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumIndexingSchema;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.plugin.DumIndexingPlugin;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.turing.client.sn.job.TurSNJobItem;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of connector context using Strategy and Chain of
 * Responsibility patterns.
 * This class has been refactored to improve scalability and maintainability.
 * 
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
@Component
public class DumConnectorContextImpl implements DumConnectorContext {

    /** How many object ids one query for other sources' rows carries (D111). */
    static final int TRACKED_QUERY_CHUNK = 500;

    private final DumConnectorIndexingService indexingService;
    private final JobItemBatchProcessor batchProcessor;
    private final JobProcessingChain processingChain;
    private final DumIndexingPlugin indexingPlugin;
    private final boolean provisionEnabled;
    private final Map<String, ConcurrentLinkedQueue<DumJobItemWithSession>> queuesBySource = new ConcurrentHashMap<>();
    private final Map<String, ReadWriteLock> locksBySource = new ConcurrentHashMap<>();

    public DumConnectorContextImpl(
            DumConnectorIndexingService indexingService,
            JobItemBatchProcessor batchProcessor,
            JobProcessingChain processingChain,
            DumIndexingPlugin indexingPlugin,
            @Value("${dumont.indexing.provision:true}") boolean provisionEnabled) {
        this.indexingService = indexingService;
        this.batchProcessor = batchProcessor;
        this.processingChain = processingChain;
        this.indexingPlugin = indexingPlugin;
        this.provisionEnabled = provisionEnabled;
    }

    /**
     * Sends a source's declared schema to the active indexing backend (roadmap
     * <strong>D63</strong>). Skipped when {@code dumont.indexing.provision=false} or the source
     * declares no fields — provisioning an empty schema would say nothing and could only do harm.
     */
    @Override
    public void provisionSchema(DumIndexingSchema schema) {
        if (!provisionEnabled || schema == null || schema.isEmpty()) {
            return;
        }
        indexingPlugin.provision(schema);
    }

    @Override
    public List<String> getObjectIdByDependency(String source, String provider,
            List<String> dependenciesObjectIdList) {
        if (dependenciesObjectIdList == null || dependenciesObjectIdList.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> dependencies = indexingService.findByDependencies(source, provider, dependenciesObjectIdList)
                .stream().distinct().toList();
        if (!dependencies.isEmpty()) {
            log.info("Found dependencies for {} - {} - {}", source, provider,
                    dependenciesObjectIdList);
            dependencies.forEach(dependency -> log.info("Dependent object: {}", dependency));
        }
        return dependencies;
    }

    @Override
    public boolean addJobItem(DumJobItemWithSession dumJobItemWithSession) {
        if (dumJobItemWithSession.turSNJobItem() != null &&
                dumJobItemWithSession.turSNJobItem().getId() != null) {
            String source = dumJobItemWithSession.session().getSource();
            log.info("Adding {} object to payload for source '{}'.",
                    dumJobItemWithSession.turSNJobItem().getId(), source);
            getQueue(source).offer(dumJobItemWithSession);
            processRemainingJobs(source);
            return true;
        } else {
            log.warn("Job item or its ID is null. Skipping addition to payload.");
            return false;
        }
    }

    @Override
    public void finishIndexing(DumConnectorSession session, boolean standalone) {
        String source = session.getSource();

        // Flush pending items BEFORE acquiring the write lock. The JMS send may
        // block on Artemis producer flow control; holding the write lock during
        // that wait blocks every addJobItem call in producer threads.
        batchProcessor.flush();

        ReadWriteLock lock = getLock(source);
        lock.writeLock().lock();
        try {
            // Handle deindexing if not standalone — queues delete jobs in the
            // buffer and updates the DB, but no longer flushes (flush happens
            // after the lock is released).
            if (!standalone) {
                deIndexObjects(session);
            }

            // Clear only the queue for this source
            queuesBySource.remove(source);

            // Save indexing stats if tracked. A standalone finish that recorded an outcome (D96) is
            // a tracked run that stopped short, and its row is what says so.
            if (!standalone || indexingService.hasRunOutcome(source, session.getProviderName())) {
                indexingService.completeIndexingStats(session.getSource(),
                        session.getProviderName());
            }

            log.info("Indexing process finished for session: {}", session.getTransactionId());
        } finally {
            lock.writeLock().unlock();
            locksBySource.remove(source);
            indexingService.finishProcessing(source);
        }

        // Final flush for delete jobs queued during deindex — outside the lock.
        if (!standalone) {
            batchProcessor.flush();
        }
    }

    @Override
    public int keepAlive(DumConnectorSession session, Collection<String> objectIds) {
        return indexingService.keepAlive(session, objectIds);
    }

    @Override
    public void endRunWithoutSession(String source) {
        indexingService.finishProcessing(source);
    }

    @Override
    public void recordRunOutcome(DumConnectorSession session, boolean partial, List<String> notes) {
        recordRunOutcome(session, partial, notes, List.of());
    }

    @Override
    public void recordRunOutcome(DumConnectorSession session, boolean partial, List<String> notes,
            List<String> warnings) {
        if (partial || (notes != null && !notes.isEmpty())) {
            log.info("Run outcome for source '{}': {}{}", session.getSource(),
                    partial ? "partial" : "complete", notes == null || notes.isEmpty() ? ""
                            : " (" + String.join("; ", notes) + ")");
        }
        if (warnings != null && !warnings.isEmpty()) {
            log.warn("Run of source '{}' raised {} warning(s): {}", session.getSource(),
                    warnings.size(), String.join("; ", warnings));
        }
        indexingService.recordRunOutcome(session.getSource(), session.getProviderName(), partial,
                notes, warnings);
    }

    @Override
    public List<DumConnectorIndexing> getIndexingItem(String objectId, String source,
            String provider) {
        return indexingService.getIndexingItem(objectId, source, provider);
    }

    private ConcurrentLinkedQueue<DumJobItemWithSession> getQueue(String source) {
        return queuesBySource.computeIfAbsent(source, k -> new ConcurrentLinkedQueue<>());
    }

    private ReadWriteLock getLock(String source) {
        return locksBySource.computeIfAbsent(source, k -> new ReentrantReadWriteLock());
    }

    /**
     * Processes all remaining jobs in the queue for a specific source.
     */
    private void processRemainingJobs(String source) {
        ReadWriteLock lock = getLock(source);
        ConcurrentLinkedQueue<DumJobItemWithSession> queue = getQueue(source);
        lock.readLock().lock();
        try {
            while (!queue.isEmpty()) {
                DumJobItemWithSession jobItem = queue.poll();
                if (jobItem != null) {
                    processingChain.process(jobItem, batchProcessor);
                }
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Handles deindexing of objects that should be removed from the index.
     * Caller is responsible for flushing the batch processor afterwards
     * (kept outside this method so the flush can run without holding the
     * source's write lock).
     */
    private void deIndexObjects(DumConnectorSession session) {
        List<DumConnectorIndexingModel> deindexedItems = indexingService.getShouldBeDeIndexedList(session);

        if (deindexedItems.isEmpty()) {
            return;
        }

        // Create delete jobs for each item to be deindexed, minus the sites another source of the
        // provider still tracks the same document on (D111)
        Map<String, List<DumConnectorIndexingModel>> trackedElsewhere = trackedByOtherSources(session,
                deindexedItems);
        deindexedItems.forEach(deIndexedItem -> {
            List<String> sites = sitesNoOtherSourceTracks(deIndexedItem,
                    trackedElsewhere.getOrDefault(deIndexedItem.getObjectId(), List.of()));
            if (sites.isEmpty()) {
                log.info("Keep {} object ({} - {}: {}): another source of {} still tracks it",
                        deIndexedItem.getObjectId(), deIndexedItem.getSource(), deIndexedItem.getLocale(),
                        session.getTransactionId(), session.getProviderName());
            } else {
                createDeleteJob(session, deIndexedItem, sites);
            }
        });

        // Remove from indexing database: this source no longer tracks any of them
        indexingService.deleteContentsToBeDeIndexed(session);
    }

    /**
     * The rows other sources of the session's provider hold for the ids about to be deindexed, by
     * object id (roadmap <strong>D111</strong>). Several sources of one provider can publish the same
     * document, and a delete removes it from the index whoever else tracks it; the other source's
     * next run would see an unchanged checksum and never send it again.
     */
    private Map<String, List<DumConnectorIndexingModel>> trackedByOtherSources(DumConnectorSession session,
            List<DumConnectorIndexingModel> deindexedItems) {
        List<String> ids = deindexedItems.stream().map(DumConnectorIndexingModel::getObjectId)
                .filter(Objects::nonNull).distinct().toList();
        Map<String, List<DumConnectorIndexingModel>> tracked = new HashMap<>();
        for (int from = 0; from < ids.size(); from += TRACKED_QUERY_CHUNK) {
            List<String> chunk = ids.subList(from, Math.min(ids.size(), from + TRACKED_QUERY_CHUNK));
            indexingService.findAllByProviderAndObjectIdIn(session.getProviderName(), chunk).stream()
                    .filter(row -> !session.getSource().equals(row.getSource()))
                    .forEach(row -> tracked.computeIfAbsent(row.getObjectId(), id -> new ArrayList<>()).add(row));
        }
        return tracked;
    }

    /** The sites of {@code item} no other source tracks the same document on, in the same locale. */
    private static List<String> sitesNoOtherSourceTracks(DumConnectorIndexingModel item,
            List<DumConnectorIndexingModel> others) {
        Set<String> kept = new HashSet<>();
        others.stream().filter(other -> Objects.equals(other.getLocale(), item.getLocale()))
                .forEach(other -> kept.addAll(other.getSites()));
        return item.getSites().stream().filter(site -> !kept.contains(site)).toList();
    }

    /**
     * Creates a delete job for a specific indexing model.
     */
    private void createDeleteJob(DumConnectorSession session,
            DumConnectorIndexingModel dumConnectorIndexing, List<String> sites) {
        log.info("DeIndex {} object ({} - {} - {} - {}: {})",
                dumConnectorIndexing.getObjectId(),
                dumConnectorIndexing.getSource(),
                dumConnectorIndexing.getEnvironment(),
                dumConnectorIndexing.getLocale(),
                sites,
                session.getTransactionId());

        createDeleteJob(session, dumConnectorIndexing.getObjectId(), sites,
                dumConnectorIndexing.getLocale());
    }

    /**
     * The one place a delete job is built, so every deindex — the one a finished run performs and
     * the deliberate one an operator applies (D68) — carries the same attributes and travels the
     * same queue.
     */
    private void createDeleteJob(DumConnectorSession session, String objectId, List<String> sites,
            Locale locale) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ID_ATTR, objectId);
        attributes.put(SOURCE_APPS_ATTR, session.getProviderName());

        TurSNJobItem deleteJobItem = new TurSNJobItem(DELETE, sites, locale, attributes);

        // Use addDeferred so a large deindex list never triggers an auto-flush
        // while the source's write lock is held. finishIndexing flushes
        // explicitly after releasing the lock. The session travels with the item (D69) so the
        // drain — whoever asks for it — files the row under this source.
        batchProcessor.addDeferred(new DumJobItemWithSession(deleteJobItem, session,
                Collections.emptySet(), false));
        // D113: the source that opened the session hears of the removal from the one place that
        // decided it, instead of re-deriving the sweep's decision.
        try {
            session.deindexed(objectId, sites, locale);
        } catch (RuntimeException e) {
            log.warn("Deindex listener of source '{}' failed for {}: {}", session.getSource(), objectId,
                    e.getMessage());
        }
    }

    /**
     * Deindexes ids the caller already decided on (roadmap <strong>D68</strong>) — an audit's
     * orphan report an operator applied. Unlike {@link #finishIndexing}, nothing here is inferred
     * from what a run did or did not touch: the set arrives named, and every id in it becomes the
     * same delete job the run-end deindex builds.
     */
    @Override
    public int deIndexById(DumConnectorSession session, Collection<String> objectIds,
            List<String> sites) {
        if (objectIds == null || objectIds.isEmpty() || sites == null || sites.isEmpty()) {
            return 0;
        }
        List<String> ids = objectIds.stream().filter(Objects::nonNull).distinct().toList();
        ids.forEach(objectId -> {
            log.info("DeIndex {} object on demand ({} - {} - {}: {})", objectId,
                    session.getSource(), sites, session.getLocale(), session.getTransactionId());
            createDeleteJob(session, objectId, sites, session.getLocale());
        });
        batchProcessor.flush();
        return ids.size();
    }
}
