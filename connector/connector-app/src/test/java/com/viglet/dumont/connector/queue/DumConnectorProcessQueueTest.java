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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.plugin.DumIndexingPlugin;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;

class DumConnectorProcessQueueTest {

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
        DumConnectorProcessQueue queue = new DumConnectorProcessQueue(plugin);

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
    void emptyJobIsIgnored() {
        CapturingPlugin plugin = new CapturingPlugin();
        DumConnectorProcessQueue queue = new DumConnectorProcessQueue(plugin);

        queue.receiveAndSendToDumont(new TurSNJobItems());

        assertEquals(null, plugin.indexed, "an empty job must not be forwarded to the backend");
    }
}
