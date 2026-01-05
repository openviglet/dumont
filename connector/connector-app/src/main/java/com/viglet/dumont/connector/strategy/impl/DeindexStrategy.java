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

import static com.viglet.dumont.commons.indexing.DumIndexingStatus.DEINDEXED;
import static com.viglet.dumont.connector.commons.logging.DumConnectorLoggingUtils.setSuccessStatus;
import static com.viglet.turing.client.sn.job.TurSNJobAction.DELETE;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.batch.JobItemBatchProcessor;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.dumont.connector.strategy.JobProcessingStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Strategy for deindexing (deleting) objects from the search index.
 * Handles DELETE job actions.
 * 
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeindexStrategy implements JobProcessingStrategy {

    private final DumConnectorIndexingService indexingService;

    @Override
    public void process(DumJobItemWithSession jobItem, JobItemBatchProcessor batchProcessor) {
        // Delete from indexing service
        indexingService.delete(jobItem);

        // Add to batch processor
        batchProcessor.add(jobItem.turSNJobItem(), jobItem.session());

        // Update status
        setSuccessStatus(jobItem, DEINDEXED);

        log.info("DeIndexed {} object", jobItem.turSNJobItem().getId());
    }

    @Override
    public boolean canHandle(DumJobItemWithSession jobItem) {
        return jobItem.turSNJobItem().getTurSNJobAction().equals(DELETE);
    }

    @Override
    public int getPriority() {
        return 10; // High priority - process deletions first
    }
}
