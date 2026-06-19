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

package com.viglet.dumont.connector.commons.spi.structured.provenance;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;

/**
 * Process-local {@link DumProvenanceHistoryStore} — the default provenance history (roadmap
 * <strong>D16</strong>). Keeps a bounded, newest-first ring per source for the lifetime of the
 * running connector; it does not survive a restart (the persisted store in {@code connector-app}
 * does). Insertion order, not wall-clock, defines the series, so two runs in the same millisecond
 * still order deterministically.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public final class DumInMemoryProvenanceHistoryStore implements DumProvenanceHistoryStore {

    /** Per-source runs retained; older runs roll off the tail. */
    private static final int MAX_HISTORY = 50;

    private final ConcurrentMap<String, Deque<DumProvenanceSnapshot>> history =
            new ConcurrentHashMap<>();

    @Override
    public DumProvenanceSnapshot recordSnapshot(String source, int documentCount,
            List<DumFieldProvenanceEntry> fields) {
        DumProvenanceSnapshot snapshot = new DumProvenanceSnapshot(source, System.currentTimeMillis(),
                documentCount, fields);
        if (source != null) {
            Deque<DumProvenanceSnapshot> runs = history.computeIfAbsent(source,
                    key -> new ConcurrentLinkedDeque<>());
            runs.addFirst(snapshot);
            while (runs.size() > MAX_HISTORY) {
                runs.pollLast();
            }
        }
        return snapshot;
    }

    @Override
    public List<DumProvenanceSnapshot> history(String source) {
        Deque<DumProvenanceSnapshot> runs = history.get(source);
        return runs == null ? List.of() : new ArrayList<>(runs);
    }
}
