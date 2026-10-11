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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Consumer;

import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;

/**
 * Counts partner API calls against the caps a source's plan allows (trabalharei
 * <strong>TRA13</strong>). Each cap is a rolling window — the last minute, day, 7 days and 30 days
 * before now — and {@link #tryAcquire()} refuses a call that would exceed any of them, so an adapter
 * stops fetching instead of burning the quota or getting the key suspended.
 *
 * <p>
 * No I/O of its own, time comes from the injected {@link Clock}. The budget only knows the calls it
 * was told about — the ones it granted plus any {@code priorCalls} it was seeded with — so a daily or
 * monthly cap holds across runs only when the caller seeds it with the calls of earlier runs and
 * records each granted call ({@link Builder#onGranted}); {@code DumJobsStructuredSource} does both
 * through the {@code job_call} table.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumJobCallBudget {

    /** The rolling windows a cap can be set on. */
    public enum Window {
        MINUTE(Duration.ofMinutes(1)),
        DAY(Duration.ofDays(1)),
        WEEK(Duration.ofDays(7)),
        MONTH(Duration.ofDays(30));

        private final Duration length;

        Window(Duration length) {
            this.length = length;
        }

        public Duration length() {
            return length;
        }
    }

    private final Map<Window, Integer> caps;
    private final Clock clock;
    private final Deque<Instant> calls = new ArrayDeque<>();
    private final Consumer<Instant> onGranted;

    private DumJobCallBudget(Map<Window, Integer> caps, Clock clock, Collection<Instant> priorCalls,
            Consumer<Instant> onGranted) {
        this.caps = caps;
        this.clock = clock;
        this.onGranted = onGranted;
        if (priorCalls != null) {
            priorCalls.stream().sorted().forEach(calls::addLast);
        }
    }

    /** A budget with no cap at all — every call is granted. */
    public static DumJobCallBudget unlimited(Clock clock) {
        return builder(clock).build();
    }

    /** The budget for {@code source}'s configured caps. */
    public static DumJobCallBudget of(DumJobSource source, Clock clock) {
        return builder(clock).caps(source, Map.of()).build();
    }

    public static Builder builder(Clock clock) {
        return new Builder(clock);
    }

    /**
     * Record one call if every cap still has room, and say whether it was granted. A refused call is
     * not recorded, so a caller may keep asking and get a {@code true} once a window rolls over.
     */
    public synchronized boolean tryAcquire() {
        Instant now = clock.instant();
        prune(now);
        for (Map.Entry<Window, Integer> cap : caps.entrySet()) {
            if (countSince(now.minus(cap.getKey().length())) >= cap.getValue()) {
                return false;
            }
        }
        calls.addLast(now);
        if (onGranted != null) {
            onGranted.accept(now);
        }
        return true;
    }

    /** Whether a call made now would be granted, without recording one. */
    public synchronized boolean canCall() {
        Instant now = clock.instant();
        prune(now);
        return caps.entrySet().stream()
                .allMatch(cap -> countSince(now.minus(cap.getKey().length())) < cap.getValue());
    }

    /**
     * Calls left in {@code window} right now, or empty when that window has no cap. The overall room
     * is the smallest of the capped windows.
     */
    public synchronized OptionalInt remaining(Window window) {
        Integer cap = caps.get(window);
        if (cap == null) {
            return OptionalInt.empty();
        }
        Instant now = clock.instant();
        prune(now);
        return OptionalInt.of(Math.max(0, cap - countSince(now.minus(window.length()))));
    }

    /** Every call still inside the longest window, oldest first — what a caller persists to seed the next run. */
    public synchronized List<Instant> recordedCalls() {
        prune(clock.instant());
        return new ArrayList<>(calls);
    }

    private int countSince(Instant from) {
        int count = 0;
        var it = calls.descendingIterator();
        while (it.hasNext() && it.next().isAfter(from)) {
            count++;
        }
        return count;
    }

    /** Forget calls older than the longest window: no cap can count them any more. */
    private void prune(Instant now) {
        Instant horizon = now.minus(Window.MONTH.length());
        while (!calls.isEmpty() && !calls.peekFirst().isAfter(horizon)) {
            calls.pollFirst();
        }
    }

    public static final class Builder {
        private final Clock clock;
        private final Map<Window, Integer> caps = new EnumMap<>(Window.class);
        private Collection<Instant> priorCalls;
        private Consumer<Instant> onGranted;

        private Builder(Clock clock) {
            if (clock == null) {
                throw new IllegalArgumentException("A call budget needs a clock");
            }
            this.clock = clock;
        }

        /** Cap {@code window} at {@code max} calls; {@code null} or {@code <= 0} leaves it uncapped. */
        public Builder cap(Window window, Integer max) {
            if (max == null || max <= 0) {
                caps.remove(window);
            } else {
                caps.put(window, max);
            }
            return this;
        }

        /**
         * Cap every window from {@code source}; a window the source leaves {@code null} falls back to
         * {@code defaults} (the adapter's plan caps). A value {@code <= 0} on the source leaves that
         * window uncapped even when a default exists.
         */
        public Builder caps(DumJobSource source, Map<Window, Integer> defaults) {
            Map<Window, Integer> fallback = defaults == null ? Map.of() : defaults;
            cap(Window.MINUTE, orDefault(source.getCapPerMinute(), fallback.get(Window.MINUTE)));
            cap(Window.DAY, orDefault(source.getCapPerDay(), fallback.get(Window.DAY)));
            cap(Window.WEEK, orDefault(source.getCapPerWeek(), fallback.get(Window.WEEK)));
            cap(Window.MONTH, orDefault(source.getCapPerMonth(), fallback.get(Window.MONTH)));
            return this;
        }

        private static Integer orDefault(Integer configured, Integer fallback) {
            return configured != null ? configured : fallback;
        }

        /** Called with the instant of every granted call, so the caller can persist it. */
        public Builder onGranted(Consumer<Instant> onGranted) {
            this.onGranted = onGranted;
            return this;
        }

        /** Calls made before this budget existed (earlier runs), so the long windows count them. */
        public Builder priorCalls(Collection<Instant> priorCalls) {
            this.priorCalls = priorCalls;
            return this;
        }

        public DumJobCallBudget build() {
            return new DumJobCallBudget(new EnumMap<>(caps), clock, priorCalls, onGranted);
        }
    }
}
