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

package com.viglet.dumont.connector.commons.http;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * Spaces outbound calls so no two go closer together than a minimum interval (roadmap
 * <strong>D92</strong>, shared since <strong>D315</strong>). The JSON API source paces its pages
 * with one; the OAI-PMH full-text fetcher paces its downloads with another.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@FunctionalInterface
public interface DumPacer {

    /** Blocks until a call spaced {@code minIntervalMs} after the previous one may go. */
    void awaitTurn(long minIntervalMs);

    /** A pacer over the wall clock that sleeps the remainder of the interval. */
    static DumPacer sleeping() {
        return sleeping(System::currentTimeMillis, ms -> {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /** A pacer over a given clock and sleep, so a test can run it without waiting. */
    static DumPacer sleeping(LongSupplier clock, LongConsumer sleep) {
        long[] last = {Long.MIN_VALUE};
        return minIntervalMs -> {
            synchronized (last) {
                long now = clock.getAsLong();
                if (last[0] != Long.MIN_VALUE && minIntervalMs > 0) {
                    long wait = last[0] + minIntervalMs - now;
                    if (wait > 0) {
                        sleep.accept(wait);
                        now += wait;
                    }
                }
                last[0] = now;
            }
        };
    }
}
