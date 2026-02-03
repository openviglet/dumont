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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.batch.JobItemBatchProcessor;
import com.viglet.dumont.connector.chain.JobProcessingChain;
import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
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
    private final ConcurrentLinkedQueue<DumJobItemWithSession> queueLinks = new ConcurrentLinkedQueue<>();

    public DumConnectorContextImpl(
            DumConnectorIndexingService indexingService,
            JobItemBatchProcessor batchProcessor,
            JobProcessingChain processingChain) {
        this.indexingService = indexingService;
        this.batchProcessor = batchProcessor;
        this.processingChain = processingChain;
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
        return indexingService.findByDependencies(source, provider, dependenciesObjectIdList)
                .stream().distinct().toList();
    }

    @Override
    public boolean addJobItem(DumJobItemWithSession dumJobItemWithSession) {
        if (dumJobItemWithSession.turSNJobItem() != null &&
                dumJobItemWithSession.turSNJobItem().getId() != null) {
            log.info("Adding {} object to payload.", dumJobItemWithSession.turSNJobItem().getId());
            queueLinks.offer(dumJobItemWithSession);
            processRemainingJobs();
            return true;
        } else {
            log.warn("Job item or its ID is null. Skipping addition to payload.");
            return false;
        }
    }

    @Override
    public void finishIndexing(DumConnectorSession session, boolean standalone) {
        // Flush any remaining items in the batch processor
        batchProcessor.flush(session);

        // Handle deindexing if not standalone
        if (!standalone) {
            deIndexObjects(session);
        }

        // Clear the queue
        queueLinks.clear();

        log.info("Indexing process finished for session: {}", session.getTransactionId());
    }

    @Override
    public List<DumConnectorIndexing> getIndexingItem(String objectId, String source,
            String provider) {
        return indexingService.getIndexingItem(objectId, source, provider);
    }

    /**
     * Processes all remaining jobs in the queue using the processing chain.
     */
    private void processRemainingJobs() {
        while (!queueLinks.isEmpty()) {
            DumJobItemWithSession jobItem = queueLinks.poll();
            if (jobItem != null) {
                processingChain.process(jobItem, batchProcessor);
            }
        }
    }

    /**
     * Handles deindexing of objects that should be removed from the index.
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

        // Flush remaining items
        batchProcessor.flush(session);
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

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ID_ATTR, dumConnectorIndexing.getObjectId());
        attributes.put(SOURCE_APPS_ATTR, session.getProviderName());

        TurSNJobItem deleteJobItem = new TurSNJobItem(
                DELETE,
                dumConnectorIndexing.getSites(),
                dumConnectorIndexing.getLocale(),
                attributes);

        batchProcessor.add(deleteJobItem, session);
    }
}
