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

package com.viglet.dumont.connector.strategy;

import com.viglet.dumont.connector.batch.JobItemBatchProcessor;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;

/**
 * Strategy interface for processing different types of job items.
 * Implementations define specific processing logic for index, reindex, deindex,
 * etc.
 * 
 * @author Alexandre Oliveira
 * @since 2026.1
 */
public interface JobProcessingStrategy {

    /**
     * Processes the job item according to the strategy's logic.
     * 
     * @param jobItem        the job item with session to process
     * @param batchProcessor the batch processor for adding items to queue
     */
    void process(DumJobItemWithSession jobItem, JobItemBatchProcessor batchProcessor);

    /**
     * Determines if this strategy can handle the given job item.
     * 
     * @param jobItem the job item to check
     * @return true if this strategy can process the job item
     */
    boolean canHandle(DumJobItemWithSession jobItem);

    /**
     * Returns the priority of this strategy.
     * Lower values have higher priority.
     * 
     * @return the priority value
     */
    default int getPriority() {
        return 100;
    }
}
