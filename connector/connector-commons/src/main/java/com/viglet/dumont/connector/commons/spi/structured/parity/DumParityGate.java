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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;

/**
 * The per-source <strong>parity gate</strong> (roadmap <strong>D11</strong>): the guard that decides
 * whether a native declarative strategy may replace the incumbent sidecar (Python) run for one
 * source, before that leg of the cursarei migration is allowed to flip.
 *
 * <p>
 * The gate runs the same two operations on both sources — {@link DumStructuredSource#discover()} for
 * the record count and {@link DumStructuredSource#extract(String)} for the canonical URL set — and
 * compares them against the {@link DumParityCriteria}. It deliberately does <em>not</em> compare full
 * documents: a leg is "at parity" when the native strategy finds the same population (count within
 * tolerance) and resolves the same public URLs (coverage), which is exactly what makes retiring the
 * Python leg safe. Enrichment-derived fields, field-by-field diffs and coverage drift are the job of
 * the D14 preview / D15 monitoring surfaces, not this gate.
 *
 * <p>
 * The gate is read-only and side-effect free; the caller persists the flip only when
 * {@link DumParityReport#passed()} is {@code true}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumParityGate {

    private static final double EPSILON = 1e-9;

    private DumParityGate() {
    }

    /**
     * Compare {@code candidate} (the native strategy) against {@code baseline} (the sidecar / Python
     * run) for one source. {@code criteria} may be {@code null}, in which case
     * {@link DumParityCriteria#defaults()} apply.
     */
    public static DumParityReport compare(DumStructuredSource baseline,
            DumStructuredSource candidate, DumParityCriteria criteria) {
        if (baseline == null || candidate == null) {
            throw new IllegalArgumentException("Parity needs both a baseline and a candidate source");
        }
        DumParityCriteria gate = criteria == null ? DumParityCriteria.defaults() : criteria;
        String source = baseline.getSourceName();

        List<String> baselineIds = safeDiscover(baseline);
        List<String> candidateIds = safeDiscover(candidate);
        int baselineCount = baselineIds.size();
        int candidateCount = candidateIds.size();
        int countDelta = Math.abs(baselineCount - candidateCount);
        double countDeltaRatio;
        if (baselineCount == 0) {
            countDeltaRatio = candidateCount == 0 ? 0.0 : 1.0;
        } else {
            countDeltaRatio = (double) countDelta / baselineCount;
        }
        boolean countWithinTolerance = countDeltaRatio <= gate.countTolerance() + EPSILON;

        Set<String> baselineUrls = urls(baseline, baselineIds, gate.urlField());
        Set<String> candidateUrls = urls(candidate, candidateIds, gate.urlField());
        int matched = (int) baselineUrls.stream().filter(candidateUrls::contains).count();
        double coverage;
        if (baselineUrls.isEmpty()) {
            coverage = candidateUrls.isEmpty() ? 1.0 : 0.0;
        } else {
            coverage = (double) matched / baselineUrls.size();
        }
        boolean coverageMet = coverage >= gate.minUrlCoverage() - EPSILON;

        List<String> missing = baselineUrls.stream().filter(url -> !candidateUrls.contains(url))
                .limit(gate.maxSampleUrls()).toList();
        List<String> extra = candidateUrls.stream().filter(url -> !baselineUrls.contains(url))
                .limit(gate.maxSampleUrls()).toList();

        List<String> reasons = new ArrayList<>();
        if (!countWithinTolerance) {
            reasons.add(String.format(Locale.ROOT,
                    "Record count off by %.1f%% (baseline %d, candidate %d) — tolerance is %.1f%%",
                    countDeltaRatio * 100, baselineCount, candidateCount,
                    gate.countTolerance() * 100));
        }
        if (!coverageMet) {
            reasons.add(String.format(Locale.ROOT,
                    "URL coverage %.1f%% below the %.1f%% gate (%d of %d baseline URLs matched)",
                    coverage * 100, gate.minUrlCoverage() * 100, matched, baselineUrls.size()));
        }

        boolean passed = countWithinTolerance && coverageMet;
        return new DumParityReport(source, baselineCount, candidateCount, countDelta, countDeltaRatio,
                countWithinTolerance, baselineUrls.size(), candidateUrls.size(), matched, coverage,
                coverageMet, missing, extra, passed, reasons);
    }

    private static List<String> safeDiscover(DumStructuredSource source) {
        List<String> ids = source.discover();
        return ids == null ? List.of() : ids;
    }

    private static Set<String> urls(DumStructuredSource source, List<String> ids, String urlField) {
        Set<String> set = new LinkedHashSet<>();
        for (String id : ids) {
            source.extract(id).ifPresent(rec -> addUrl(set, rec.values().get(urlField)));
        }
        return set;
    }

    private static void addUrl(Set<String> set, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.forEach(element -> addUrl(set, element));
            return;
        }
        String url = value.toString().trim();
        if (!url.isEmpty()) {
            set.add(url);
        }
    }
}
