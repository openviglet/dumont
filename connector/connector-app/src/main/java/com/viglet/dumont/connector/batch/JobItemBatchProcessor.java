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

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.logging.DumJobTrail;
import com.viglet.turing.client.sn.job.TurSNJobItems;

import lombok.extern.slf4j.Slf4j;

/**
 * Batch processor for managing job items efficiently.
 * Handles buffering and sending job items to the message queue.
 *
 * <p>
 * One buffer serves every source, so an item carries the session that produced it (roadmap
 * <strong>D69</strong>) rather than borrowing whichever session happened to ask for the drain —
 * which filed a concurrent run's {@code SENT_TO_QUEUE} entries under the wrong source and
 * transaction id. A flush is a request to send what is buffered, not a claim about who owns it.
 *
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
@Component
public class JobItemBatchProcessor {

    private final List<DumJobItemWithSession> buffer = new ArrayList<>();
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
     * @param item the job item and the session that produced it
     */
    public void add(DumJobItemWithSession item) {
        List<DumJobItemWithSession> toSend = null;
        synchronized (this) {
            buffer.add(item);
            logQueueInfo();
            if (buffer.size() >= batchSize) {
                toSend = drainBuffer();
            }
        }
        if (toSend != null) {
            sendBatch(toSend);
        }
    }

    /**
     * Adds a job item to the buffer without triggering an auto-flush.
     * The caller is responsible for invoking {@link #flush()}
     * once the batch is complete. Useful when adding many items under a lock
     * where the JMS send must not happen until after the lock is released.
     *
     * @param item the job item and the session that produced it
     */
    public synchronized void addDeferred(DumJobItemWithSession item) {
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
     * <p>
     * The drain covers everything buffered, whichever source put it there. That is what the
     * caller wants — the queue is shared — and it is safe because each item carries its own
     * session, so no row is attributed to the caller that asked for the send.
     */
    public void flush() {
        List<DumJobItemWithSession> toSend;
        synchronized (this) {
            toSend = drainBuffer();
        }
        if (toSend != null) {
            sendBatch(toSend);
        }
    }

    private List<DumJobItemWithSession> drainBuffer() {
        if (buffer.isEmpty()) {
            log.debug("No job to drain from buffer.");
            return null;
        }

        log.info("Draining {} jobs from buffer.", buffer.size());

        if (log.isDebugEnabled()) {
            for (DumJobItemWithSession buffered : buffer) {
                log.debug("TurSNJobItem Id: {}",
                        buffered.turSNJobItem().getAttributes().get("id"));
            }
        }

        List<DumJobItemWithSession> drained = List.copyOf(buffer);
        buffer.clear();
        return drained;
    }

    private void sendBatch(List<DumJobItemWithSession> drained) {
        TurSNJobItems itemsToSend = new TurSNJobItems();
        for (DumJobItemWithSession buffered : drained) {
            // D69: the session that produced the item, never the one that asked for the drain —
            // otherwise a concurrent run's rows are filed under whoever happened to flush.
            setSuccessStatus(buffered.turSNJobItem(), buffered.session(), SENT_TO_QUEUE);
            // D71: and the same pair travels with it, because the session does not cross the
            // queue and the listener has to file its own row under the same source.
            DumJobTrail.stamp(buffered.turSNJobItem(), buffered.session());
            itemsToSend.add(buffered.turSNJobItem());
        }
        jmsMessagingTemplate.convertAndSend(CONNECTOR_INDEXING_QUEUE, itemsToSend);
        log.info("Successfully sent batch of {} jobs to connector queue.", itemsToSend.size());
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
