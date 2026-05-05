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

package com.viglet.dumont.connector.batch;

import static com.viglet.dumont.commons.indexing.DumIndexingStatus.SENT_TO_QUEUE;
import static com.viglet.dumont.connector.commons.logging.DumConnectorLoggingUtils.setSuccessStatus;
import static com.viglet.dumont.connector.constant.DumConnectorConstants.CONNECTOR_INDEXING_QUEUE;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;

import lombok.extern.slf4j.Slf4j;

/**
 * Batch processor for managing job items efficiently.
 * Handles buffering and sending job items to the message queue.
 * 
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
@Component
public class JobItemBatchProcessor {

    private final TurSNJobItems buffer = new TurSNJobItems();
    private final JmsMessagingTemplate jmsMessagingTemplate;
    private final int batchSize;

    public JobItemBatchProcessor(
            @Value("${dumont.job.size:50}") int batchSize,
            JmsMessagingTemplate jmsMessagingTemplate) {
        this.batchSize = batchSize;
        this.jmsMessagingTemplate = jmsMessagingTemplate;
    }

    /**
     * Adds a job item to the buffer.
     * Automatically flushes when batch size is reached.
     * The buffer mutation runs under the instance monitor; the JMS send,
     * if triggered, happens AFTER the monitor is released so that other
     * threads calling {@link #add} are not blocked waiting on Artemis
     * producer flow control.
     *
     * @param item    the job item to add
     * @param session the connector session
     */
    public void add(TurSNJobItem item, DumConnectorSession session) {
        TurSNJobItems toSend = null;
        synchronized (this) {
            buffer.add(item);
            logQueueInfo();
            if (buffer.size() >= batchSize) {
                toSend = drainBuffer(session);
            }
        }
        if (toSend != null) {
            sendBatch(toSend);
        }
    }

    /**
     * Adds a job item to the buffer without triggering an auto-flush.
     * The caller is responsible for invoking {@link #flush(DumConnectorSession)}
     * once the batch is complete. Useful when adding many items under a lock
     * where the JMS send must not happen until after the lock is released.
     *
     * @param item the job item to add
     */
    public synchronized void addDeferred(TurSNJobItem item) {
        buffer.add(item);
        logQueueInfo();
    }

    /**
     * Flushes all buffered items to the message queue.
     * The buffer is drained atomically under the instance monitor; the
     * JMS send happens AFTER the monitor is released so that producer
     * threads calling {@link #add} or {@link #addDeferred} are not
     * blocked while Artemis flow control throttles this thread.
     *
     * @param session the connector session
     */
    public void flush(DumConnectorSession session) {
        TurSNJobItems toSend;
        synchronized (this) {
            toSend = drainBuffer(session);
        }
        if (toSend != null) {
            sendBatch(toSend);
        }
    }

    private TurSNJobItems drainBuffer(DumConnectorSession session) {
        if (buffer.size() == 0) {
            log.debug("No job to drain from buffer.");
            return null;
        }

        log.info("Draining {} jobs from buffer.", buffer.size());

        if (log.isDebugEnabled()) {
            for (TurSNJobItem turSNJobItem : buffer) {
                log.debug("TurSNJobItem Id: {}", turSNJobItem.getAttributes().get("id"));
            }
        }

        TurSNJobItems itemsToSend = new TurSNJobItems();
        for (TurSNJobItem turSNJobItem : buffer) {
            setSuccessStatus(turSNJobItem, session, SENT_TO_QUEUE);
            itemsToSend.add(turSNJobItem);
        }
        buffer.clear();
        return itemsToSend;
    }

    private void sendBatch(TurSNJobItems items) {
        jmsMessagingTemplate.convertAndSend(CONNECTOR_INDEXING_QUEUE, items);
        log.info("Successfully sent batch of {} jobs to connector queue.", items.size());
    }

    /**
     * Returns the current buffer size.
     * 
     * @return the number of items in the buffer
     */
    public synchronized int size() {
        return buffer.size();
    }

    /**
     * Checks if the buffer is empty.
     * 
     * @return true if buffer has no items
     */
    public synchronized boolean isEmpty() {
        return buffer.size() == 0;
    }

    private void logQueueInfo() {
        log.debug("Total Job Items in buffer: {}", buffer.size());
    }
}
