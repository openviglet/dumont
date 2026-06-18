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

package com.viglet.dumont.connector.commons.spi.structured;

/**
 * Outcome of one {@link DumBoundedExtraction#extractEach extractEach} pass (roadmap
 * <strong>D33</strong>): did the run get through its whole discovered id set, or was it cut short?
 *
 * <p>
 * The signal lets {@code indexAll} on the structured loop distinguish a <em>complete</em> run (every
 * discovered id was extracted — stale content can be safely deindexed) from an <em>interrupted</em>
 * one (pool/JVM shutdown breaks the extraction loop before all ids were visited). A partial run must
 * <strong>not</strong> drive the tracking-driven deindex sweep: every id not yet extracted would be
 * treated as removed and wiped, so a transient interruption would destroy a still-valid index. The
 * plugin finishes such a run <em>standalone</em> instead, generalising D09's zero-yield safe-run to
 * the partial / aborted structured path.
 *
 * @param discovered  the number of ids the run set out to extract
 * @param processed   the number of ids it actually got through before finishing or being cut short
 * @param interrupted {@code true} when the extraction loop broke on an interrupt before visiting
 *                    every discovered id
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record DumExtractionOutcome(int discovered, int processed, boolean interrupted) {

    /** A run that visited every discovered id. */
    public static DumExtractionOutcome completed(int discovered) {
        return new DumExtractionOutcome(discovered, discovered, false);
    }

    /** A run cut short by an interrupt after {@code processed} of {@code discovered} ids. */
    public static DumExtractionOutcome interrupted(int discovered, int processed) {
        return new DumExtractionOutcome(discovered, processed, true);
    }

    /**
     * {@code true} when the run got through its whole discovered id set, so a downstream deindex
     * sweep is safe; {@code false} when it was interrupted partway and the prior index must be kept.
     */
    public boolean completed() {
        return !interrupted;
    }
}
