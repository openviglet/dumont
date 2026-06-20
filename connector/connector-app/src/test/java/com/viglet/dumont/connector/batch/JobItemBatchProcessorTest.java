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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jms.core.JmsMessagingTemplate;

import com.viglet.dumont.connector.commons.DumConnectorSession;
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
        return new DumConnectorSession("wknd", "turing", Locale.US);
    }

    @Test
    void addBuffersUntilBatchSizeThenFlushes() {
        processor.add(item("c1"), session());
        assertThat(processor.size()).isEqualTo(1);
        assertThat(processor.isEmpty()).isFalse();
        verify(jms, never()).convertAndSend(anyString(), any(Object.class));

        processor.add(item("c2"), session());

        verify(jms, times(1)).convertAndSend(anyString(), any(TurSNJobItems.class));
        assertThat(processor.size()).isZero();
    }

    @Test
    void deferredAddDoesNotFlushUntilExplicitFlush() {
        processor.addDeferred(item("c1"));
        processor.addDeferred(item("c2"));
        processor.addDeferred(item("c3"));
        verify(jms, never()).convertAndSend(anyString(), any(Object.class));

        processor.flush(session());

        verify(jms, times(1)).convertAndSend(anyString(), any(TurSNJobItems.class));
        assertThat(processor.isEmpty()).isTrue();
    }

    @Test
    void flushWithEmptyBufferDoesNotSend() {
        processor.flush(session());
        verify(jms, never()).convertAndSend(anyString(), any(Object.class));
    }
}
