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

package com.viglet.dumont.connector.plugin.jobs.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobCallBudget.Window;

class DumJobCallBudgetTest {

    private static final Instant T0 = Instant.parse("2026-10-10T12:00:00Z");

    private final MutableClock clock = new MutableClock(T0);

    @Test
    void unlimitedGrantsEveryCall() {
        DumJobCallBudget budget = DumJobCallBudget.unlimited(clock);
        for (int i = 0; i < 1000; i++) {
            assertThat(budget.tryAcquire()).isTrue();
        }
        assertThat(budget.remaining(Window.DAY)).isEmpty();
    }

    @Test
    void perMinuteCapRefusesPastTheCapAndRollsOver() {
        DumJobCallBudget budget = DumJobCallBudget.builder(clock).cap(Window.MINUTE, 2).build();

        assertThat(budget.tryAcquire()).isTrue();
        assertThat(budget.tryAcquire()).isTrue();
        assertThat(budget.tryAcquire()).isFalse();
        assertThat(budget.canCall()).isFalse();
        assertThat(budget.remaining(Window.MINUTE)).hasValue(0);

        clock.advance(Duration.ofSeconds(61));
        assertThat(budget.canCall()).isTrue();
        assertThat(budget.tryAcquire()).isTrue();
    }

    @Test
    void refusedCallsAreNotCounted() {
        DumJobCallBudget budget = DumJobCallBudget.builder(clock).cap(Window.MINUTE, 1).build();
        assertThat(budget.tryAcquire()).isTrue();
        assertThat(budget.tryAcquire()).isFalse();
        assertThat(budget.tryAcquire()).isFalse();
        assertThat(budget.recordedCalls()).hasSize(1);
    }

    @Test
    void theTightestWindowWins() {
        DumJobCallBudget budget = DumJobCallBudget.builder(clock)
                .cap(Window.MINUTE, 10).cap(Window.DAY, 3).build();
        assertThat(budget.tryAcquire()).isTrue();
        clock.advance(Duration.ofMinutes(5));
        assertThat(budget.tryAcquire()).isTrue();
        clock.advance(Duration.ofMinutes(5));
        assertThat(budget.tryAcquire()).isTrue();
        clock.advance(Duration.ofMinutes(5));
        // Minute window is empty again, but the day already holds 3 calls.
        assertThat(budget.tryAcquire()).isFalse();
        assertThat(budget.remaining(Window.MINUTE)).hasValue(10);
        assertThat(budget.remaining(Window.DAY)).hasValue(0);

        clock.advance(Duration.ofDays(1));
        assertThat(budget.tryAcquire()).isTrue();
    }

    @Test
    void weekAndMonthCapsCountPriorCalls() {
        List<Instant> earlierRuns = List.of(T0.minus(Duration.ofDays(20)),
                T0.minus(Duration.ofDays(3)), T0.minus(Duration.ofDays(2)));
        DumJobCallBudget budget = DumJobCallBudget.builder(clock)
                .cap(Window.WEEK, 3).cap(Window.MONTH, 4).priorCalls(earlierRuns).build();

        // Week holds 2 prior calls, month holds 3: one call left before either cap.
        assertThat(budget.remaining(Window.WEEK)).hasValue(1);
        assertThat(budget.remaining(Window.MONTH)).hasValue(1);
        assertThat(budget.tryAcquire()).isTrue();
        assertThat(budget.tryAcquire()).isFalse();

        // After 11 days the 20-day-old call leaves the 30-day window, and the week is empty again.
        clock.advance(Duration.ofDays(11));
        assertThat(budget.remaining(Window.MONTH)).hasValue(1);
        assertThat(budget.tryAcquire()).isTrue();
        assertThat(budget.tryAcquire()).isFalse();
    }

    @Test
    void capsComeFromTheSourceAndNonPositiveMeansUncapped() {
        DumJobSource source = DumJobSource.builder().capPerMinute(1).capPerDay(0).build();
        DumJobCallBudget budget = DumJobCallBudget.of(source, clock);
        assertThat(budget.remaining(Window.MINUTE)).hasValue(1);
        assertThat(budget.remaining(Window.DAY)).isEmpty();
        assertThat(budget.remaining(Window.WEEK)).isEmpty();
        assertThat(budget.tryAcquire()).isTrue();
        assertThat(budget.tryAcquire()).isFalse();
    }
}
