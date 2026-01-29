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

package com.viglet.dumont.connector.strategy.impl;

import static com.viglet.dumont.commons.indexing.DumIndexingStatus.IGNORED;
import static com.viglet.dumont.commons.indexing.DumIndexingStatus.PREPARE_FORCED_REINDEX;
import static com.viglet.dumont.commons.indexing.DumIndexingStatus.PREPARE_REINDEX;
import static com.viglet.dumont.connector.commons.logging.DumConnectorLoggingUtils.setSuccessStatus;

import java.util.List;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.batch.JobItemBatchProcessor;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.dumont.connector.strategy.JobProcessingStrategy;
import com.viglet.turing.client.sn.job.TurSNJobItem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Strategy for reindexing existing objects when content has changed.
 * Handles job items that exist but have different checksums or were previously
 * ignored.
 * 
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReindexStrategy implements JobProcessingStrategy {

    private final DumConnectorIndexingService indexingService;

    @Override
    public void process(DumJobItemWithSession jobItem, JobItemBatchProcessor batchProcessor) {
        // Log the reindex operation
        logReindex(jobItem);

        // Add to batch processor
        batchProcessor.add(jobItem.turSNJobItem(), jobItem.session());

        // Update indexing status
        List<DumConnectorIndexingModel> indexingModelList = indexingService.getList(jobItem);

        if (indexingModelList.size() > 1) {
            // Handle duplicated entries
            recreateDuplicatedIndexing(jobItem);
        } else {
            // Normal reindex
            indexingService.update(jobItem, indexingModelList, PREPARE_REINDEX);
            setSuccessStatus(jobItem, PREPARE_REINDEX);
            log.info("Updated status {}", getObjectDetailForLogs(jobItem));
        }
    }

    @Override
    public boolean canHandle(DumJobItemWithSession jobItem) {
        return indexingService.exists(jobItem)
                && (indexingService.isChecksumDifferent(jobItem) || hasIgnoredStatus(jobItem));
    }

    @Override
    public int getPriority() {
        return 40; // After index strategy
    }

    private void logReindex(DumJobItemWithSession jobItem) {
        indexingService.getList(jobItem)
                .forEach(indexing -> log.info("ReIndexed {} from {} to {}",
                        getObjectDetailForLogs(jobItem),
                        indexing.getChecksum(),
                        jobItem.turSNJobItem().getChecksum()));
    }

    private void recreateDuplicatedIndexing(DumJobItemWithSession jobItem) {
        indexingService.deindexedStatus(jobItem);
        log.info("Removed duplicated status {}", getObjectDetailForLogs(jobItem));

        indexingService.save(jobItem, PREPARE_FORCED_REINDEX);
        setSuccessStatus(jobItem, PREPARE_FORCED_REINDEX);
        log.info("Recreated status {}", getObjectDetailForLogs(jobItem));
    }

    private boolean hasIgnoredStatus(DumJobItemWithSession jobItem) {
        return indexingService.getList(jobItem).stream()
                .anyMatch(indexing -> IGNORED.equals(indexing.getStatus()));
    }

    private String getObjectDetailForLogs(DumJobItemWithSession jobItem) {
        TurSNJobItem turSNJobItem = jobItem.turSNJobItem();
        return "%s object (%s - %s - %s: %s)".formatted(
                turSNJobItem.getId(),
                jobItem.session().getSource(),
                turSNJobItem.getEnvironment(),
                turSNJobItem.getLocale(),
                jobItem.session().getTransactionId());
    }
}
