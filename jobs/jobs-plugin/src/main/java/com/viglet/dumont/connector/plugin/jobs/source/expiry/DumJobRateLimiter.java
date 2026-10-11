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

package com.viglet.dumont.connector.plugin.jobs.source.expiry;

import java.util.function.LongSupplier;

/**
 * Spaces requests evenly: at most {@code perSecond} a second, by making each caller wait until
 * {@code 1/perSecond} seconds after the previous permit (trabalharei <strong>TRA24</strong>). Not a
 * call budget: it never refuses, it only delays. {@code perSecond <= 0} never waits.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public final class DumJobRateLimiter {

    /** How the limiter waits; the seam lets a test advance a fake clock instead of sleeping. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long nanos) throws InterruptedException;
    }

    private final long intervalNanos;
    private final LongSupplier nanoTime;
    private final Sleeper sleeper;
    private boolean started;
    private long next;

    /** A limiter on the system clock. */
    public DumJobRateLimiter(double perSecond) {
        this(perSecond, System::nanoTime,
                nanos -> Thread.sleep(nanos / 1_000_000, (int) (nanos % 1_000_000)));
    }

    public DumJobRateLimiter(double perSecond, LongSupplier nanoTime, Sleeper sleeper) {
        this.intervalNanos = perSecond > 0 ? Math.round(1_000_000_000d / perSecond) : 0;
        this.nanoTime = nanoTime;
        this.sleeper = sleeper;
    }

    /** Wait for the next permit. */
    public synchronized void acquire() throws InterruptedException {
        if (intervalNanos <= 0) {
            return;
        }
        long now = nanoTime.getAsLong();
        if (started && next - now > 0) {
            sleeper.sleep(next - now);
            now = next;
        }
        started = true;
        next = now + intervalNanos;
    }
}
