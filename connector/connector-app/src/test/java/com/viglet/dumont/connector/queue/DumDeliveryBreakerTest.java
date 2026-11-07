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

package com.viglet.dumont.connector.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.plugin.DumIndexingPlugin;

/** D343: consecutive failed deliveries stop a run only when the backend is down. */
class DumDeliveryBreakerTest {

    private final DumIndexingPlugin plugin = mock(DumIndexingPlugin.class);

    @Test
    void opensAtTheThresholdWhenTheBackendIsDown() {
        when(plugin.unreachable()).thenReturn(Optional.of("Viglet Turing unreachable at http://t"));
        DumDeliveryBreaker breaker = new DumDeliveryBreaker(plugin, 3);

        breaker.failed("src");
        breaker.failed("src");
        assertThat(breaker.stopped("src")).isEmpty();
        verify(plugin, never()).unreachable();

        breaker.failed("src");
        assertThat(breaker.stopped("src")).contains("Viglet Turing unreachable at http://t");
        assertThat(breaker.stopped("other")).isEmpty();
    }

    @Test
    void staysClosedWhenTheBackendAnswers() {
        when(plugin.unreachable()).thenReturn(Optional.empty());
        DumDeliveryBreaker breaker = new DumDeliveryBreaker(plugin, 2);

        breaker.failed("src");
        breaker.failed("src");

        assertThat(breaker.stopped("src")).isEmpty();
    }

    @Test
    void aDeliveryInBetweenMeansTheFailuresWereNotInARow() {
        DumDeliveryBreaker breaker = new DumDeliveryBreaker(plugin, 2);

        breaker.failed("src");
        breaker.delivered("src");
        breaker.failed("src");

        verify(plugin, never()).unreachable();
        assertThat(breaker.stopped("src")).isEmpty();
    }

    @Test
    void theNextRunStartsClosed() {
        when(plugin.unreachable()).thenReturn(Optional.of("down"));
        DumDeliveryBreaker breaker = new DumDeliveryBreaker(plugin, 1);
        breaker.failed("src");
        assertThat(breaker.stopped("src")).isPresent();

        breaker.reset("src");

        assertThat(breaker.stopped("src")).isEmpty();
    }
}
