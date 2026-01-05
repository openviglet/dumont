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

import static com.viglet.dumont.commons.indexing.DumIndexingStatus.PREPARE_INDEX;
import static com.viglet.dumont.connector.commons.logging.DumConnectorLoggingUtils.setSuccessStatus;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.batch.JobItemBatchProcessor;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.dumont.connector.strategy.JobProcessingStrategy;
import com.viglet.turing.client.sn.job.TurSNJobItem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Strategy for indexing new objects.
 * Handles job items that need to be indexed for the first time.
 * 
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexStrategy implements JobProcessingStrategy {

    private final DumConnectorIndexingService indexingService;

    @Override
    public void process(DumJobItemWithSession jobItem, JobItemBatchProcessor batchProcessor) {
        // Save indexing record
        indexingService.save(jobItem, PREPARE_INDEX);

        // Add to batch processor
        batchProcessor.add(jobItem.turSNJobItem(), jobItem.session());

        // Update status
        setSuccessStatus(jobItem, PREPARE_INDEX);

        log.info("Created status {}", getObjectDetailForLogs(jobItem));
    }

    @Override
    public boolean canHandle(DumJobItemWithSession jobItem) {
        TurSNJobItem turSNJobItem = jobItem.turSNJobItem();
        return StringUtils.isNotEmpty(turSNJobItem.getId())
                && !indexingService.exists(jobItem);
    }

    @Override
    public int getPriority() {
        return 30; // Lower priority than deindex and ignore rules
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
