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

package com.viglet.dumont.connector.queue;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.plugin.DumIndexingPlugin;

import lombok.extern.slf4j.Slf4j;

/**
 * Stops a run whose deliveries all fail because the backend is down (roadmap <strong>D343</strong>).
 *
 * <p>
 * Delivery is asynchronous: a connector reads its source and enqueues batches, and the queue
 * listener delivers them later. A failed delivery was recorded (D138) but never reached the run,
 * so with Viglet Turing down a crawl read every page and enqueued batches that all failed. This
 * counts consecutive failed deliveries per source; at {@code dumont.delivery.breaker.failures}
 * it asks the backend whether it answers, and when it does not, the source's run is stopped: the
 * next document it offers is refused, and its finish neither sweeps nor reads as clean. A refused
 * batch on a backend that answers is a content problem, so the run goes on.
 *
 * <p>
 * The documents whose delivery failed are already marked for resending (D138), and the ones the
 * run never read are untouched, so the next run sends both.
 */
@Slf4j
@Component
public class DumDeliveryBreaker {

    private final DumIndexingPlugin indexingPlugin;
    private final int threshold;
    private final Map<String, Integer> failures = new ConcurrentHashMap<>();
    private final Map<String, String> stopped = new ConcurrentHashMap<>();

    public DumDeliveryBreaker(DumIndexingPlugin indexingPlugin,
            @Value("${dumont.delivery.breaker.failures:3}") int threshold) {
        this.indexingPlugin = indexingPlugin;
        this.threshold = Math.max(1, threshold);
    }

    /** A batch from {@code source} was not delivered. */
    public void failed(String source) {
        int count = failures.merge(source, 1, Integer::sum);
        if (count < threshold || stopped.containsKey(source)) {
            return;
        }
        indexingPlugin.unreachable().ifPresentOrElse(reason -> {
            stopped.put(source, reason);
            log.error("Source '{}': {} deliveries failed in a row and {} — its run stops reading the "
                    + "source", source, count, reason);
        }, () -> log.warn("Source '{}': {} deliveries failed in a row, but {} answers, so the run "
                + "goes on", source, count, indexingPlugin.getProviderName()));
    }

    /** A batch from {@code source} was delivered: the failures were not in a row. */
    public void delivered(String source) {
        failures.remove(source);
    }

    /** Why the run of {@code source} was stopped, if it was. */
    public Optional<String> stopped(String source) {
        return Optional.ofNullable(stopped.get(source));
    }

    /** The run of {@code source} ended: the next one starts with a closed breaker. */
    public void reset(String source) {
        failures.remove(source);
        stopped.remove(source);
    }
}
