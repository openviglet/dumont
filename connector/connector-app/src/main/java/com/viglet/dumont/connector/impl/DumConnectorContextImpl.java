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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
        batchProcessor.flush(session);

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

            // Save indexing stats if tracked
            if (!standalone) {
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
            batchProcessor.flush(session);
        }
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

        // Create delete jobs for each item to be deindexed
        deindexedItems.forEach(deIndexedItem -> createDeleteJob(session, deIndexedItem));

        // Remove from indexing database
        indexingService.deleteContentsToBeDeIndexed(session);
    }

    /**
     * Creates a delete job for a specific indexing model.
     */
    private void createDeleteJob(DumConnectorSession session,
            DumConnectorIndexingModel dumConnectorIndexing) {
        log.info("DeIndex {} object ({} - {} - {}: {})",
                dumConnectorIndexing.getObjectId(),
                dumConnectorIndexing.getSource(),
                dumConnectorIndexing.getEnvironment(),
                dumConnectorIndexing.getLocale(),
                session.getTransactionId());

        createDeleteJob(session, dumConnectorIndexing.getObjectId(),
                dumConnectorIndexing.getSites(), dumConnectorIndexing.getLocale());
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
        // explicitly after releasing the lock.
        batchProcessor.addDeferred(deleteJobItem);
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
        batchProcessor.flush(session);
        return ids.size();
    }
}
