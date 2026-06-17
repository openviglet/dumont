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

import java.util.List;

/**
 * Per-field last-seen provenance over a source's run history (roadmap <strong>D16</strong>): for each
 * field the source ever declared, the origin it was last extracted from and how long ago.
 *
 * <p>
 * This is what makes a silently-dropped field auditable. A field still present in the latest run has
 * {@link FieldLastSeen#lastSeenRunsAgo() lastSeenRunsAgo} {@code = 0}; a field the source stopped
 * declaring shows a positive value — "last seen {@code N} runs ago" — together with the origin it
 * used to come from, so a consumer can tell the field disappeared rather than was never there.
 *
 * @param source the source the history belongs to
 * @param fields per field ever seen, the last-seen origin and recency (latest-run order)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public record DumProvenanceLastSeen(String source, List<FieldLastSeen> fields) {

    public DumProvenanceLastSeen {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    /** The neutral result for a source with no run history yet. */
    public static DumProvenanceLastSeen none(String source) {
        return new DumProvenanceLastSeen(source, List.of());
    }

    /** Names of fields not present in the latest run (declared before, gone now). */
    public List<String> droppedFields() {
        return fields.stream().filter(field -> !field.presentInLatest()).map(FieldLastSeen::field)
                .toList();
    }

    /**
     * One field's last-seen provenance.
     *
     * @param field           the declared field name
     * @param strategy        the strategy it was last extracted by (or {@code null} if never seen)
     * @param selector        the selector/path it was last read from (or {@code null})
     * @param confidence      the last recorded confidence (or {@code null})
     * @param presentInLatest whether the latest run still carried the field
     * @param lastSeenRunsAgo {@code 0} when present in the latest run, {@code N > 0} when last seen
     *                        {@code N} runs back, {@code -1} when never seen in the history
     * @param lastSeenAt      the {@code capturedAt} of the run it was last seen in (epoch millis, or
     *                        {@code 0} when never seen)
     */
    public record FieldLastSeen(String field, String strategy, String selector, Double confidence,
            boolean presentInLatest, int lastSeenRunsAgo, long lastSeenAt) {
    }
}
