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

package com.viglet.dumont.connector.commons.spi.structured.coverage;

import java.util.List;
import java.util.Optional;

/**
 * Per-source persistence seam for the coverage history a drift check (roadmap <strong>D15</strong>)
 * reads.
 *
 * <p>
 * A snapshot is appended at the end of every full index run and the history is read back to compare
 * runs and to render the trend. The seam keeps the structured-source commons free of any storage
 * choice — the app supplies the implementation (an in-memory ring by default; a persisted store in
 * {@code connector-app}). Unlike the D14 baseline (one snapshot per source), this keeps a
 * <em>series</em> so coverage is queryable across runs.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public interface DumCoverageHistoryStore {

    /**
     * Append one run's coverage to the source's history and return the stored snapshot (the
     * implementation stamps {@code capturedAt}).
     */
    DumCoverageSnapshot record(String source, int documentCount, List<DumFieldCoverage> fields);

    /** The source's coverage history, newest run first (possibly empty, never null). */
    List<DumCoverageSnapshot> history(String source);

    /** The most recent run's coverage, if the source has ever been fully indexed. */
    default Optional<DumCoverageSnapshot> latest(String source) {
        List<DumCoverageSnapshot> history = history(source);
        return history.isEmpty() ? Optional.empty() : Optional.of(history.get(0));
    }
}
