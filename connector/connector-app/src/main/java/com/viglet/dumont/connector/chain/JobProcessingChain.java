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

package com.viglet.dumont.connector.chain;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.batch.JobItemBatchProcessor;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.strategy.JobProcessingStrategy;

import lombok.extern.slf4j.Slf4j;

/**
 * Chain of Responsibility implementation for processing job items.
 * Routes job items to the appropriate strategy based on priority and
 * capability.
 * 
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
@Component
public class JobProcessingChain {

    private final List<JobProcessingStrategy> strategies;

    public JobProcessingChain(List<JobProcessingStrategy> strategies) {
        // Sort strategies by priority (lower values first)
        this.strategies = strategies.stream()
                .sorted(Comparator.comparingInt(JobProcessingStrategy::getPriority))
                .toList();

        log.info("Initialized JobProcessingChain with {} strategies", strategies.size());
        strategies.forEach(strategy -> log.debug("Strategy: {} with priority {}",
                strategy.getClass().getSimpleName(),
                strategy.getPriority()));
    }

    /**
     * Processes a job item by finding and executing the first matching strategy.
     * 
     * @param jobItem        the job item to process
     * @param batchProcessor the batch processor for queueing items
     */
    public void process(DumJobItemWithSession jobItem, JobItemBatchProcessor batchProcessor) {
        if (jobItem == null || jobItem.turSNJobItem() == null) {
            log.warn("Received null job item, skipping processing");
            return;
        }

        strategies.stream()
                .filter(strategy -> strategy.canHandle(jobItem))
                .findFirst()
                .ifPresentOrElse(
                        strategy -> {
                            log.debug("Processing {} with {}",
                                    jobItem.turSNJobItem().getId(),
                                    strategy.getClass().getSimpleName());
                            strategy.process(jobItem, batchProcessor);
                        },
                        () -> log.warn("No strategy found for job item: {} (action: {})",
                                jobItem.turSNJobItem().getId(),
                                jobItem.turSNJobItem().getTurSNJobAction()));
    }

    /**
     * Returns the list of registered strategies.
     * 
     * @return list of strategies sorted by priority
     */
    public List<JobProcessingStrategy> getStrategies() {
        return strategies;
    }
}
