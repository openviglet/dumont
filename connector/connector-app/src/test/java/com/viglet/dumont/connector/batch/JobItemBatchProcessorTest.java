/*
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

package com.viglet.dumont.connector.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.jms.core.JmsMessagingTemplate;

import com.viglet.dumont.commons.indexing.DumIndexingStatus;
import com.viglet.dumont.commons.logging.DumLoggingIndexing;
import com.viglet.dumont.commons.logging.DumLoggingIndexingLog;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;

class JobItemBatchProcessorTest {

    private JmsMessagingTemplate jms;
    private JobItemBatchProcessor processor;

    @BeforeEach
    void setUp() {
        jms = mock(JmsMessagingTemplate.class);
        processor = new JobItemBatchProcessor(2, jms);
    }

    private static TurSNJobItem item(String id) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", id);
        return new TurSNJobItem(TurSNJobAction.CREATE, List.of("Sample"), Locale.US, attrs);
    }

    private static DumConnectorSession session() {
        return session("wknd");
    }

    private static DumConnectorSession session(String source) {
        return new DumConnectorSession(source, "turing", Locale.US);
    }

    private static DumJobItemWithSession buffered(String id, DumConnectorSession session) {
        return new DumJobItemWithSession(item(id), session, Collections.emptySet(), false);
    }

    @Test
    void addBuffersUntilBatchSizeThenFlushes() {
        processor.add(buffered("c1", session()));
        assertThat(processor.size()).isEqualTo(1);
        assertThat(processor.isEmpty()).isFalse();
        verify(jms, never()).convertAndSend(anyString(), any(Object.class));

        processor.add(buffered("c2", session()));

        verify(jms, times(1)).convertAndSend(anyString(), any(TurSNJobItems.class));
        assertThat(processor.size()).isZero();
    }

    @Test
    void deferredAddDoesNotFlushUntilExplicitFlush() {
        processor.addDeferred(buffered("c1", session()));
        processor.addDeferred(buffered("c2", session()));
        processor.addDeferred(buffered("c3", session()));
        verify(jms, never()).convertAndSend(anyString(), any(Object.class));

        processor.flush();

        verify(jms, times(1)).convertAndSend(anyString(), any(TurSNJobItems.class));
        assertThat(processor.isEmpty()).isTrue();
    }

    @Test
    void flushWithEmptyBufferDoesNotSend() {
        processor.flush();
        verify(jms, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("each item is logged under the session that produced it, not the flushing one (D69)")
    void aDrainStampsEachItemWithItsOwnSession() {
        // One buffer serves every source, so two runs can be mid-buffer at the same moment.
        DumConnectorSession wknd = session("wknd");
        DumConnectorSession docs = session("docs");
        processor.addDeferred(buffered("/wknd/a", wknd));
        processor.addDeferred(buffered("/docs/a", docs));

        ArgumentCaptor<DumLoggingIndexing> rows = ArgumentCaptor.forClass(DumLoggingIndexing.class);
        try (MockedStatic<DumLoggingIndexingLog> logged = mockStatic(DumLoggingIndexingLog.class)) {
            // A third session asks for the drain — it owns neither item.
            processor.flush();
            logged.verify(() -> DumLoggingIndexingLog.setStatus(rows.capture()), times(2));
        }

        assertThat(rows.getAllValues())
                .extracting(DumLoggingIndexing::getContentId, DumLoggingIndexing::getSource,
                        DumLoggingIndexing::getTransactionId, DumLoggingIndexing::getStatus)
                .containsExactly(
                        tuple("/wknd/a", "wknd", wknd.getTransactionId(),
                                DumIndexingStatus.SENT_TO_QUEUE),
                        tuple("/docs/a", "docs", docs.getTransactionId(),
                                DumIndexingStatus.SENT_TO_QUEUE));
    }

    @Test
    @DisplayName("the auto-flush stamps the same way — it fires under whoever crossed the size")
    void theAutoFlushIsAttributedPerItemToo() {
        DumConnectorSession wknd = session("wknd");
        DumConnectorSession docs = session("docs");
        processor.add(buffered("/wknd/a", wknd));

        ArgumentCaptor<DumLoggingIndexing> rows = ArgumentCaptor.forClass(DumLoggingIndexing.class);
        try (MockedStatic<DumLoggingIndexingLog> logged = mockStatic(DumLoggingIndexingLog.class)) {
            // The batch size is 2: this add crosses it and drains both.
            processor.add(buffered("/docs/a", docs));
            logged.verify(() -> DumLoggingIndexingLog.setStatus(rows.capture()), times(2));
        }

        assertThat(rows.getAllValues()).extracting(DumLoggingIndexing::getSource)
                .containsExactly("wknd", "docs");
    }
}
