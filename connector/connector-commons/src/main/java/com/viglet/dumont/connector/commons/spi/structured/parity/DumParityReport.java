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

package com.viglet.dumont.connector.commons.spi.structured.parity;

import java.util.List;

/**
 * The verdict of a {@link DumParityGate} comparison between a baseline (the incumbent sidecar /
 * Python run) and a candidate (the native declarative strategy) for one source (roadmap
 * <strong>D11</strong>).
 *
 * <p>
 * A migration leg flips from the sidecar to native only when {@link #passed()} is {@code true}. When
 * it is {@code false}, {@link #reasons()} explains which check failed and {@link #missingUrls()} /
 * {@link #extraUrls()} carry a bounded sample of the divergence for the operator to inspect before
 * touching the recipe.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record DumParityReport(String source, int baselineCount, int candidateCount, int countDelta,
        double countDeltaRatio, boolean countWithinTolerance, int baselineUrlCount,
        int candidateUrlCount, int matchedUrlCount, double urlCoverage, boolean urlCoverageMet,
        List<String> missingUrls, List<String> extraUrls, boolean passed, List<String> reasons) {

    public DumParityReport {
        missingUrls = missingUrls == null ? List.of() : List.copyOf(missingUrls);
        extraUrls = extraUrls == null ? List.of() : List.copyOf(extraUrls);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
