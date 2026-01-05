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

import static com.viglet.dumont.commons.indexing.DumIndexingStatus.PREPARE_UNCHANGED;
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
 * Strategy for handling unchanged objects.
 * When an object exists and hasn't changed, it doesn't need reindexing.
 * 
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnchangedStrategy implements JobProcessingStrategy {

    private final DumConnectorIndexingService indexingService;

    @Override
    public void process(DumJobItemWithSession jobItem, JobItemBatchProcessor batchProcessor) {
        // Log unchanged status
        if (indexingService.exists(jobItem)) {
            log.info("Unchanged {}", getObjectDetailForLogs(jobItem));
        }

        // Update indexing status
        List<DumConnectorIndexingModel> indexingModelList = indexingService.getList(jobItem);
        indexingService.update(jobItem, indexingModelList, PREPARE_UNCHANGED);

        // Set success status
        setSuccessStatus(jobItem.turSNJobItem(), PREPARE_UNCHANGED);

        // Note: unchanged items are NOT added to the batch processor
        // They don't need to be sent to the queue
    }

    @Override
    public boolean canHandle(DumJobItemWithSession jobItem) {
        // This is the default/fallback strategy
        // Only handle if exists and no other strategy has handled it
        return indexingService.exists(jobItem);
    }

    @Override
    public int getPriority() {
        return 50; // Lowest priority - this is the fallback
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
