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
import static org.mockito.Mockito.mockStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.viglet.dumont.commons.indexing.DumIndexingStatus;
import com.viglet.dumont.commons.logging.DumLoggingIndexing;
import com.viglet.dumont.commons.logging.DumLoggingIndexingLog;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;

/**
 * Every row this writes names its source and its run (roadmap D71).
 *
 * <p>
 * These assertions used to be {@code assertDoesNotThrow}, which passed just as happily while six
 * call sites filed rows attributed to nobody — the bug D71 exists to fix. What a log row is *for*
 * is being read back, so what a test of it must assert is the content.
 */
class DumConnectorLoggingUtilsTest {

    private static final String SOURCE = "wknd";

    private static TurSNJobItem item() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "/content/wknd/a");
        attributes.put(DumConnectorLoggingUtils.URL, "https://example.test/a");
        return new TurSNJobItem(TurSNJobAction.CREATE, List.of("Sample"), Locale.US, attributes);
    }

    private static DumConnectorSession session() {
        return new DumConnectorSession(SOURCE, "turing", Locale.US);
    }

    /** Runs `write` with the log stubbed, and hands back the single row it filed. */
    private static DumLoggingIndexing rowFiledBy(Consumer<Void> write) {
        ArgumentCaptor<DumLoggingIndexing> row = ArgumentCaptor.forClass(DumLoggingIndexing.class);
        try (MockedStatic<DumLoggingIndexingLog> logged = mockStatic(DumLoggingIndexingLog.class)) {
            write.accept(null);
            logged.verify(() -> DumLoggingIndexingLog.setStatus(row.capture()));
        }
        return row.getValue();
    }

    @Test
    @DisplayName("the session overload files the source and the transaction id")
    void aSessionNamesTheRow() {
        DumConnectorSession session = session();

        DumLoggingIndexing row = rowFiledBy(
                ignored -> DumConnectorLoggingUtils.setSuccessStatus(item(), session,
                        DumIndexingStatus.INDEXED));

        assertEquals(SOURCE, row.getSource());
        assertEquals(session.getTransactionId(), row.getTransactionId());
        assertEquals("/content/wknd/a", row.getContentId());
        assertEquals("https://example.test/a", row.getUrl());
        assertEquals(DumIndexingStatus.INDEXED, row.getStatus());
    }

    @Test
    @DisplayName("the item-with-session overload does not drop the session it is handed")
    void anItemWithSessionKeepsIt() {
        // The whole defect in one line: this overload used to delegate to a source-less one, so
        // every PREPARE_* and IGNORED row a strategy filed named nobody.
        DumConnectorSession session = session();
        DumJobItemWithSession withSession =
                new DumJobItemWithSession(item(), session, Set.of(), false);

        DumLoggingIndexing row = rowFiledBy(
                ignored -> DumConnectorLoggingUtils.setSuccessStatus(withSession,
                        DumIndexingStatus.PREPARE_INDEX));

        assertEquals(SOURCE, row.getSource());
        assertEquals(session.getTransactionId(), row.getTransactionId());
    }

    @Test
    @DisplayName("details ride along without displacing the attribution")
    void detailsDoNotDisplaceTheAttribution() {
        DumConnectorSession session = session();

        DumLoggingIndexing row = rowFiledBy(
                ignored -> DumConnectorLoggingUtils.setSuccessStatus(item(), session,
                        DumIndexingStatus.INDEXED, "skipped a field"));

        assertEquals("skipped a field", row.getDetails());
        assertEquals(SOURCE, row.getSource());
    }

    @Test
    @DisplayName("named source and transaction id are what the queue side uses")
    void namedValuesAreCarriedThrough() {
        // Past the queue there is no session, so this overload is the only one that can file a row
        // at all — it takes the two values the item's trail carried across.
        DumLoggingIndexing row = rowFiledBy(
                ignored -> DumConnectorLoggingUtils.setSuccessStatus(item(), SOURCE, "tx-1",
                        DumIndexingStatus.RECEIVED_AND_SENT_TO_TURING, null));

        assertEquals(SOURCE, row.getSource());
        assertEquals("tx-1", row.getTransactionId());
    }

    @Test
    void urlConstantIsTheAttributeNameTheRowReads() {
        assertEquals("url", DumConnectorLoggingUtils.URL);
    }
}
