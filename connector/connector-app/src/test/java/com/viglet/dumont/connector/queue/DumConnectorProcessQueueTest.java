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

package com.viglet.dumont.connector.queue;

import static com.viglet.dumont.commons.indexing.DumIndexingStatus.RECEIVED_AND_SENT_TO_TURING;
import static com.viglet.dumont.commons.indexing.DumIndexingStatus.SENT_TO_QUEUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.viglet.dumont.commons.logging.DumLoggingIndexing;
import com.viglet.dumont.commons.logging.DumLoggingIndexingLog;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.logging.DumConnectorLoggingUtils;
import com.viglet.dumont.connector.commons.logging.DumJobTrail;
import com.viglet.dumont.connector.commons.plugin.DumIndexingDeliveryException;
import com.viglet.dumont.connector.commons.plugin.DumIndexingPlugin;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;

class DumConnectorProcessQueueTest {

    private final DumConnectorIndexingService indexingService = mock(DumConnectorIndexingService.class);

    @Test
    @DisplayName("a batch the backend refuses is marked for resending under its source (D138)")
    void aRefusedBatchIsMarkedForResendingInsteadOfLeftIndexed() {
        DumIndexingPlugin refusing = new DumIndexingPlugin() {
            @Override
            public void index(TurSNJobItems turSNJobItems) {
                throw new DumIndexingDeliveryException("No route to host");
            }

            @Override
            public String getProviderName() {
                return "TURING";
            }
        };
        DumConnectorProcessQueue queue = new DumConnectorProcessQueue(refusing, indexingService);
        DumConnectorSession session = new DumConnectorSession("jobs", "turing", Locale.US);
        TurSNJobItems items = new TurSNJobItems();
        for (String id : List.of("job-1", "job-2")) {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("id", id);
            TurSNJobItem jobItem = new TurSNJobItem(TurSNJobAction.CREATE, List.of("Sample"), Locale.US,
                    attributes);
            DumJobTrail.stamp(jobItem, session);
            items.add(jobItem);
        }
        Map<String, Object> gone = new HashMap<>();
        gone.put("id", "job-0");
        TurSNJobItem delete = new TurSNJobItem(TurSNJobAction.DELETE, List.of("Sample"), Locale.US, gone);
        DumJobTrail.stamp(delete, session);
        items.add(delete);

        rowFiledBy(() -> queue.receiveAndSendToDumont(items));

        verify(indexingService).recordDeliveryFailure("jobs", List.of("job-1", "job-2"), "No route to host");
    }

    /** Captures the job items the queue hands to the configured indexing backend. */
    private static final class CapturingPlugin implements DumIndexingPlugin {
        private TurSNJobItems indexed;

        @Override
        public void index(TurSNJobItems turSNJobItems) {
            this.indexed = turSNJobItems;
        }

        @Override
        public String getProviderName() {
            return "CAPTURING";
        }
    }

    private TurSNJobItem itemById(TurSNJobItems items, String id) {
        for (TurSNJobItem item : items) {
            if (id.equals(item.getAttributes().get("id"))) {
                return item;
            }
        }
        throw new AssertionError("No job item with id " + id);
    }

    @Test
    void groundsEmptyAttributesBeforeIndexingRegardlessOfConnector() {
        CapturingPlugin plugin = new CapturingPlugin();
        DumConnectorProcessQueue queue = new DumConnectorProcessQueue(plugin, indexingService);

        // A loose-map job item, as a non-structured connector (web crawler / AEM) would build it.
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "c1");
        attributes.put("title", "Algorithms");
        attributes.put("description", ""); // blank at the source -> must be omitted
        attributes.put("area", Arrays.asList("CS", "", null)); // cleaned to ["CS"]
        attributes.put("tags", List.of()); // empty -> omitted

        TurSNJobItems items = new TurSNJobItems();
        items.add(new TurSNJobItem(TurSNJobAction.CREATE, List.of("Sample"), Locale.US, attributes));

        queue.receiveAndSendToDumont(items);

        assertNotNull(plugin.indexed, "queue must forward grounded items to the backend");
        TurSNJobItem indexed = itemById(plugin.indexed, "c1");

        assertEquals("Algorithms", indexed.getAttributes().get("title"));
        assertEquals(List.of("CS"), indexed.getAttributes().get("area"));
        assertFalse(indexed.getAttributes().containsKey("description"),
                "absent field must not reach the backend as an empty string (facet pollution)");
        assertFalse(indexed.getAttributes().containsKey("tags"),
                "empty collection must be omitted, not indexed");
    }

    @Test
    @DisplayName("a document's log names the same source at every status it passes through (D71)")
    void theTrailSurvivesTheQueueAndComesOffBeforeIndexing() {
        CapturingPlugin plugin = new CapturingPlugin();
        DumConnectorProcessQueue queue = new DumConnectorProcessQueue(plugin, indexingService);
        DumConnectorSession session = new DumConnectorSession("wknd", "turing", Locale.US);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "c1");
        attributes.put("title", "Algorithms");
        TurSNJobItem jobItem =
                new TurSNJobItem(TurSNJobAction.CREATE, List.of("Sample"), Locale.US, attributes);

        // What the batch processor does on its way out: file the SENT_TO_QUEUE row under the
        // session, and stamp the same pair onto the item so the far side can file its own.
        DumLoggingIndexing sent = rowFiledBy(
                () -> DumConnectorLoggingUtils.setSuccessStatus(jobItem, session, SENT_TO_QUEUE));
        DumJobTrail.stamp(jobItem, session);

        TurSNJobItems items = new TurSNJobItems();
        items.add(jobItem);

        DumLoggingIndexing delivered = rowFiledBy(() -> queue.receiveAndSendToDumont(items));

        // The property, stated the way the roadmap's criterion states it.
        assertEquals(sent.getSource(), delivered.getSource());
        assertEquals(sent.getTransactionId(), delivered.getTransactionId());
        assertEquals("wknd", delivered.getSource());
        assertEquals(RECEIVED_AND_SENT_TO_TURING, delivered.getStatus());

        // And the trail is bookkeeping, so it never becomes a field on the indexed document.
        TurSNJobItem indexed = itemById(plugin.indexed, "c1");
        assertFalse(indexed.getAttributes().containsKey(DumJobTrail.SOURCE));
        assertFalse(indexed.getAttributes().containsKey(DumJobTrail.TRANSACTION_ID));
        assertEquals("Algorithms", indexed.getAttributes().get("title"));
    }

    /** Runs `write` with the indexing log stubbed, and hands back the last row it filed. */
    private static DumLoggingIndexing rowFiledBy(Runnable write) {
        ArgumentCaptor<DumLoggingIndexing> rows =
                ArgumentCaptor.forClass(DumLoggingIndexing.class);
        try (MockedStatic<DumLoggingIndexingLog> logged = mockStatic(DumLoggingIndexingLog.class)) {
            write.run();
            logged.verify(() -> DumLoggingIndexingLog.setStatus(rows.capture()), atLeastOnce());
        }
        return rows.getValue();
    }

    @Test
    void emptyJobIsIgnored() {
        CapturingPlugin plugin = new CapturingPlugin();
        DumConnectorProcessQueue queue = new DumConnectorProcessQueue(plugin, indexingService);

        queue.receiveAndSendToDumont(new TurSNJobItems());

        assertEquals(null, plugin.indexed, "an empty job must not be forwarded to the backend");
    }
}
