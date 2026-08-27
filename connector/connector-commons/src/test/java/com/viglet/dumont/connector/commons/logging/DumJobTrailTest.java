/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
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

package com.viglet.dumont.connector.commons.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;

/** What survives the queue, and what must not survive past it (roadmap D71). */
class DumJobTrailTest {

    private static TurSNJobItem item(Map<String, Object> attributes) {
        return new TurSNJobItem(TurSNJobAction.CREATE, List.of("Sample"), Locale.US, attributes);
    }

    @Test
    @DisplayName("the stamp carries the source and the run across the queue")
    void theStampCarriesTheSourceAndTheRun() {
        DumConnectorSession session = new DumConnectorSession("wknd", "turing", Locale.US);
        TurSNJobItem jobItem = item(Map.of("id", "/a"));

        DumJobTrail.stamp(jobItem, session);

        assertEquals("wknd", DumJobTrail.source(jobItem));
        assertEquals(session.getTransactionId(), DumJobTrail.transactionId(jobItem));
        assertEquals("/a", jobItem.getAttributes().get("id"), "the payload is untouched");
    }

    @Test
    @DisplayName("an immutable attribute map is replaced, not mutated")
    void anImmutableAttributeMapIsReplaced() {
        // A plugin is free to build its attributes with Map.of, and a stamp that tried to put into
        // one would throw and fail the whole batch rather than one item.
        TurSNJobItem jobItem = item(Map.of("id", "/a"));

        assertDoesNotThrow(() -> DumJobTrail
                .stamp(jobItem, new DumConnectorSession("wknd", "turing", Locale.US)));
        assertEquals("wknd", DumJobTrail.source(jobItem));
    }

    @Test
    @DisplayName("the trail comes off before the batch reaches an indexing plugin")
    void theTrailComesOffBeforeIndexing() {
        DumConnectorSession session = new DumConnectorSession("wknd", "turing", Locale.US);
        TurSNJobItem jobItem = item(Map.of("id", "/a", "title", "Algorithms"));
        DumJobTrail.stamp(jobItem, session);

        Map<String, Object> stripped = DumJobTrail.strip(jobItem.getAttributes());

        assertFalse(stripped.containsKey(DumJobTrail.SOURCE),
                "connector bookkeeping must not become a field on the indexed document");
        assertFalse(stripped.containsKey(DumJobTrail.TRANSACTION_ID));
        assertTrue(stripped.containsKey("title"), "the payload survives the strip");
        assertEquals("/a", stripped.get("id"));
    }

    @Test
    @DisplayName("an item that predates the stamp reads as unattributed rather than throwing")
    void anUnstampedItemReadsAsNull() {
        // Messages already on the queue when the connector restarts carry no trail.
        TurSNJobItem jobItem = item(Map.of("id", "/a"));

        assertNull(DumJobTrail.source(jobItem));
        assertNull(DumJobTrail.transactionId(jobItem));
        assertNull(DumJobTrail.source(null));
        assertNull(DumJobTrail.strip(null));
    }

    @Test
    void aNullSessionStampsNothing() {
        TurSNJobItem jobItem = item(Map.of("id", "/a"));

        DumJobTrail.stamp(jobItem, null);

        assertFalse(jobItem.getAttributes().containsKey(DumJobTrail.SOURCE));
    }
}
