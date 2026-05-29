/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.plugin.eds.fetch;

import java.util.concurrent.TimeUnit;

/**
 * Minimal token-bucket rate limiter. Smooth, no burst capacity beyond a single
 * token: each {@link #acquire()} either takes the one available token or sleeps
 * until the next slot is due. Thread-safe via instance-level synchronisation.
 *
 * <p>Used per-source so a slow site does not starve a fast one and so we never
 * exceed EDS's 200 req/s/host limit. Defaults are conservative — operators tune
 * via {@code DumEDSSource.requestsPerSecond}.
 */
public final class DumEDSRateLimiter {

    /** Sentinel rate that disables limiting altogether. */
    public static final double UNLIMITED = 0d;

    private final double permitsPerSecond;
    private final long minIntervalNanos;
    private long nextFreeSlotNanos;

    public DumEDSRateLimiter(double permitsPerSecond) {
        if (permitsPerSecond < 0) {
            throw new IllegalArgumentException("permitsPerSecond must be >= 0");
        }
        this.permitsPerSecond = permitsPerSecond;
        this.minIntervalNanos = permitsPerSecond == UNLIMITED
                ? 0L
                : (long) (TimeUnit.SECONDS.toNanos(1) / permitsPerSecond);
        this.nextFreeSlotNanos = System.nanoTime();
    }

    public double permitsPerSecond() {
        return permitsPerSecond;
    }

    /**
     * Blocks until a permit can be issued. Interrupting the thread restores the
     * interrupt flag and returns immediately without holding a permit.
     */
    public void acquire() {
        if (permitsPerSecond == UNLIMITED) {
            return;
        }
        long waitNanos;
        synchronized (this) {
            long now = System.nanoTime();
            if (now >= nextFreeSlotNanos) {
                nextFreeSlotNanos = now + minIntervalNanos;
                return;
            }
            waitNanos = nextFreeSlotNanos - now;
            nextFreeSlotNanos += minIntervalNanos;
        }
        try {
            TimeUnit.NANOSECONDS.sleep(waitNanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
