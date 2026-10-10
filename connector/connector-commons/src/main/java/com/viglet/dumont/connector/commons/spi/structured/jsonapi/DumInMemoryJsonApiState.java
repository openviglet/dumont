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

package com.viglet.dumont.connector.commons.spi.structured.jsonapi;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link DumJsonApiState} held in memory (roadmap <strong>D92</strong>): enough for a test and for
 * a source with no budget windows and no incremental mode, and lost on restart otherwise.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumInMemoryJsonApiState implements DumJsonApiState {

    private final Map<String, Long> calls = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();

    @Override
    public long calls(Window window, Instant windowStart) {
        return calls.getOrDefault(key(window, windowStart), 0L);
    }

    @Override
    public void addCall(Window window, Instant windowStart) {
        calls.merge(key(window, windowStart), 1L, Long::sum);
    }

    @Override
    public void markSeen(Collection<String> ids, Instant at) {
        ids.forEach(id -> lastSeen.put(id, at));
    }

    @Override
    public synchronized List<String> expire(Instant cutoff) {
        List<String> expired = new ArrayList<>();
        lastSeen.forEach((id, seen) -> {
            if (seen.isBefore(cutoff)) {
                expired.add(id);
            }
        });
        expired.forEach(lastSeen::remove);
        return expired;
    }

    private static String key(Window window, Instant windowStart) {
        return window + "@" + windowStart;
    }
}
