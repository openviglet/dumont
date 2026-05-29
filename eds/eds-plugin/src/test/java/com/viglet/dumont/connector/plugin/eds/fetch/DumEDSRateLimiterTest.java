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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DumEDSRateLimiterTest {

    @Test
    void firstAcquireReturnsImmediately() {
        DumEDSRateLimiter limiter = new DumEDSRateLimiter(10);

        long start = System.nanoTime();
        limiter.acquire();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThan(50);
    }

    @Test
    void enforcesMinIntervalBetweenAcquires() {
        // 20 permits/s -> 50ms between permits
        DumEDSRateLimiter limiter = new DumEDSRateLimiter(20);

        long start = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            limiter.acquire();
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // 4 inter-acquire waits of ~50ms each → ≥ 200ms. Allow scheduler slop.
        assertThat(elapsedMs).isGreaterThanOrEqualTo(180);
    }

    @Test
    void unlimitedRateDoesNotBlock() {
        DumEDSRateLimiter limiter = new DumEDSRateLimiter(DumEDSRateLimiter.UNLIMITED);

        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            limiter.acquire();
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThan(50);
    }

    @Test
    void rejectsNegativeRate() {
        assertThatThrownBy(() -> new DumEDSRateLimiter(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
