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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenanceLastSeen.FieldLastSeen;

/**
 * The last-seen engine (roadmap <strong>D16</strong>): a pure function over a source's provenance
 * history that says, per field, the most recent run it was present in and the origin it came from.
 *
 * <p>
 * It elevates the grounding contract from "omit absent" to "every present field is traceable, and a
 * field that stopped arriving is visible as last-seen at run {@code N}". A field present in the latest
 * run reports {@code lastSeenRunsAgo = 0}; one the source stopped declaring keeps the origin it last
 * used, so the disappearance is auditable rather than silent.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public final class DumProvenanceMonitor {

    private DumProvenanceMonitor() {
    }

    /**
     * Compute per-field last-seen provenance from {@code history}, which must be newest-run first (as
     * every {@link DumProvenanceHistoryStore} returns it). Fields are listed in the latest run's
     * declaration order, then any field only older runs declared. Returns a
     * {@link DumProvenanceLastSeen#none neutral} result for empty history.
     */
    public static DumProvenanceLastSeen lastSeen(List<DumProvenanceSnapshot> history) {
        if (history == null || history.isEmpty()) {
            return DumProvenanceLastSeen.none(null);
        }
        DumProvenanceSnapshot latest = history.get(0);

        Set<String> names = new LinkedHashSet<>();
        history.forEach(run -> run.fields().forEach(entry -> names.add(entry.field())));

        List<FieldLastSeen> fields = new ArrayList<>();
        for (String name : names) {
            boolean presentInLatest = latest.field(name).map(DumFieldProvenanceEntry::isPresent)
                    .orElse(false);
            fields.add(resolveLastSeen(name, history, presentInLatest));
        }
        return new DumProvenanceLastSeen(latest.source(), fields);
    }

    private static FieldLastSeen resolveLastSeen(String name, List<DumProvenanceSnapshot> history,
            boolean presentInLatest) {
        for (int runsAgo = 0; runsAgo < history.size(); runsAgo++) {
            DumFieldProvenanceEntry entry = history.get(runsAgo).field(name).orElse(null);
            if (entry != null && entry.isPresent()) {
                return new FieldLastSeen(name, entry.strategy(), entry.selector(), entry.confidence(),
                        presentInLatest, runsAgo, history.get(runsAgo).capturedAt());
            }
        }
        // Declared in the schema across runs but never carried by any document.
        return new FieldLastSeen(name, null, null, null, false, -1, 0L);
    }
}
