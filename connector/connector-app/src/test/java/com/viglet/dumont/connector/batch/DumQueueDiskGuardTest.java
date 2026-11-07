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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;
import org.springframework.jms.core.JmsMessagingTemplate;

import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;

/** D341: a send the broker would block on forever is refused, with the reason, before it starts. */
class DumQueueDiskGuardTest {

    private static DumQueueDiskGuard guard(double used) {
        return new DumQueueDiskGuard(Path.of("store/queue"), p -> OptionalDouble.of(used));
    }

    @Test
    void aDiskOverTheBrokersLimitIsRefusedWithTheReason() {
        assertThatThrownBy(() -> guard(97).check())
                .isInstanceOf(DumQueueDiskFullException.class)
                .hasMessageContaining("the queue's disk is over its limit")
                .hasMessageContaining("97%")
                .hasMessageContaining("95%");
    }

    @Test
    void aDiskUnderTheLimitOrUnreadableSends() {
        assertThatCode(() -> guard(60).check()).doesNotThrowAnyException();
        assertThatCode(() -> new DumQueueDiskGuard(Path.of("x"), p -> OptionalDouble.empty()).check())
                .doesNotThrowAnyException();
    }

    @Test
    void theRealDiskIsReadThroughADirectoryNotYetCreated() {
        assertThat(DumQueueDiskGuard.usedPercentOf(Path.of("target", "no-such-dir", "queue")))
                .isPresent();
    }

    @Test
    void aRefusedBatchNeverReachesTheQueue() {
        JmsMessagingTemplate jms = mock(JmsMessagingTemplate.class);
        JobItemBatchProcessor processor = new JobItemBatchProcessor(1, jms, guard(99));
        HashMap<String, Object> attrs = new HashMap<>();
        attrs.put("id", "a");
        TurSNJobItem item = new TurSNJobItem(TurSNJobAction.CREATE, List.of("Sample"), Locale.US, attrs);

        assertThatThrownBy(() -> processor.add(new DumJobItemWithSession(item,
                new DumConnectorSession("src", "CATALOG", Locale.US), Collections.emptySet(), false)))
                .isInstanceOf(DumQueueDiskFullException.class);
        verify(jms, never()).convertAndSend(anyString(), any(Object.class));
    }
}
