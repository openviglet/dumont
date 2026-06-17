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
import java.util.Optional;

/**
 * Per-source persistence seam for the provenance history a last-seen check (roadmap
 * <strong>D16</strong>) reads.
 *
 * <p>
 * A snapshot is appended at the end of every full index run and read back to tell, per field, when it
 * was last seen and the origin it was extracted from. Like the D15 coverage seam, it keeps a
 * <em>series</em> per source and leaves the storage choice to the app (an in-memory ring by default;
 * a persisted store in {@code connector-app}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public interface DumProvenanceHistoryStore {

    /**
     * Append one run's per-field provenance to the source's history and return the stored snapshot
     * (the implementation stamps {@code capturedAt}).
     */
    DumProvenanceSnapshot record(String source, int documentCount,
            List<DumFieldProvenanceEntry> fields);

    /** The source's provenance history, newest run first (possibly empty, never null). */
    List<DumProvenanceSnapshot> history(String source);

    /** The most recent run's provenance, if the source has ever been fully indexed. */
    default Optional<DumProvenanceSnapshot> latest(String source) {
        List<DumProvenanceSnapshot> history = history(source);
        return history.isEmpty() ? Optional.empty() : Optional.of(history.get(0));
    }
}
