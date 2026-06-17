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

/**
 * The thresholds a {@link DumParityGate} run must clear for a candidate source to be declared at
 * parity with its baseline (roadmap <strong>D11</strong>).
 *
 * <p>
 * The gate has exactly two checks, matching the roadmap's "count + URL sample vs the Python run":
 * <ul>
 * <li>{@link #countTolerance()} — the maximum allowed <em>relative</em> difference between the
 * baseline and candidate record counts (a fraction, so {@code 0.02} is 2%). A small tolerance
 * absorbs benign churn between two crawls without masking a real loss.</li>
 * <li>{@link #minUrlCoverage()} — the minimum fraction of the baseline's {@link #urlField() URL}
 * values the candidate must also produce. Comparing on the grounded, mandatory URL field is what
 * keeps the gate honest: a candidate that drops or rewrites course pages fails even if its raw count
 * happens to match.</li>
 * </ul>
 *
 * @param countTolerance maximum relative record-count difference, in {@code [0, 1]}
 * @param minUrlCoverage minimum baseline-URL coverage required, in {@code [0, 1]}
 * @param urlField       the schema field holding the canonical URL compared across the two runs
 * @param maxSampleUrls  how many divergent URLs to keep in the report for human inspection
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record DumParityCriteria(double countTolerance, double minUrlCoverage, String urlField,
        int maxSampleUrls) {

    public DumParityCriteria {
        if (countTolerance < 0 || countTolerance > 1) {
            throw new IllegalArgumentException("countTolerance must be in [0,1]: " + countTolerance);
        }
        if (minUrlCoverage < 0 || minUrlCoverage > 1) {
            throw new IllegalArgumentException("minUrlCoverage must be in [0,1]: " + minUrlCoverage);
        }
        if (urlField == null || urlField.isBlank()) {
            throw new IllegalArgumentException("A URL field name is required");
        }
        if (maxSampleUrls < 0) {
            throw new IllegalArgumentException("maxSampleUrls cannot be negative: " + maxSampleUrls);
        }
        urlField = urlField.trim();
    }

    /** A conservative default gate: 2% count tolerance, 98% URL coverage, the {@code url} field. */
    public static DumParityCriteria defaults() {
        return new DumParityCriteria(0.02, 0.98, "url", 20);
    }

    public DumParityCriteria withCountTolerance(double countTolerance) {
        return new DumParityCriteria(countTolerance, minUrlCoverage, urlField, maxSampleUrls);
    }

    public DumParityCriteria withMinUrlCoverage(double minUrlCoverage) {
        return new DumParityCriteria(countTolerance, minUrlCoverage, urlField, maxSampleUrls);
    }

    public DumParityCriteria withUrlField(String urlField) {
        return new DumParityCriteria(countTolerance, minUrlCoverage, urlField, maxSampleUrls);
    }

    public DumParityCriteria withMaxSampleUrls(int maxSampleUrls) {
        return new DumParityCriteria(countTolerance, minUrlCoverage, urlField, maxSampleUrls);
    }
}
