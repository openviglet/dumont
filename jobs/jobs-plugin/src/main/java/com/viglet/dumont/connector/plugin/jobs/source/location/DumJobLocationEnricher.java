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

package com.viglet.dumont.connector.plugin.jobs.source.location;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnricher;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeLibrary;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeRef;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumTaxonomy;
import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobSourceAdapter;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobsStructuredSource;

import lombok.extern.slf4j.Slf4j;

/**
 * The single canonical location step of every job record (trabalharei <strong>TRA20</strong>): a D04
 * {@link DumEnricher} that writes the IBGE city name (with accents) and UF code, and {@code remote =
 * remoto} for a place that only says the work is remote. The matching rules are
 * {@link DumJobLocationResolver}'s.
 *
 * <p>
 * The municipality list is the D19 taxonomy {@link DumIbgeMunicipalities#TAXONOMY}, resolved from a
 * {@link DumRecipeLibrary} by reference on every record, so registering a newer version re-applies on
 * the next record without rebuilding the chain (the resolver is rebuilt once per version).
 *
 * <p>
 * <strong>Inputs.</strong> A {@link DumStructuredRecord} only holds schema fields, so the raw text an
 * adapter keeps under {@link DumJobSourceAdapter#LOCATION_RAW} cannot travel on it; the enricher reads
 * {@code city}, {@code state} and that key from the posting through
 * {@link DumJobsStructuredSource#posting(String)}. {@code extract} leaves {@code city} and
 * {@code state} off the record for the same reason the other way round: an enricher cannot remove a
 * value, and this one must be able to drop a city.
 *
 * <p>
 * <strong>Unresolved places</strong> are kept as raw text under
 * {@link DumJobSourceAdapter#LOCATION_UNRESOLVED} on the posting (not a schema field, not indexed) and
 * counted per source; {@link #logSummary(String)} writes one line per source per run.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public final class DumJobLocationEnricher implements DumEnricher {

    /** The enricher name, also the provenance tag of the fields it writes. */
    public static final String NAME = "job-location";

    /** Per-source counts of one run. */
    public record Summary(int records, int resolved, int unresolved) {
    }

    private record Cached(DumTaxonomy taxonomy, DumJobLocationResolver resolver) {
    }

    private static final class Counter {
        final AtomicInteger records = new AtomicInteger();
        final AtomicInteger resolved = new AtomicInteger();
        final AtomicInteger unresolved = new AtomicInteger();

        Summary snapshot() {
            return new Summary(records.get(), resolved.get(), unresolved.get());
        }
    }

    private final DumRecipeLibrary library;
    private final DumRecipeRef taxonomyRef;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private volatile Cached cached;

    public DumJobLocationEnricher(DumRecipeLibrary library, DumRecipeRef taxonomyRef) {
        this.library = library;
        this.taxonomyRef = taxonomyRef;
    }

    /**
     * The production enricher: a library private to the jobs connector holding the shipped IBGE list,
     * tracked at its latest version. (A {@code DumRecipeLibrary} bean is not used: the catalog
     * connector already declares one, and a second bean of the type would make its injection
     * ambiguous.)
     */
    public static DumJobLocationEnricher standard() {
        return new DumJobLocationEnricher(new DumRecipeLibrary().register(DumIbgeMunicipalities.load()),
                DumRecipeRef.latest(DumIbgeMunicipalities.TAXONOMY));
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord rec) {
        Map<String, Object> posting = source instanceof DumJobsStructuredSource jobs
                ? jobs.posting(rec.id())
                : null;
        String city = text(posting, rec, DumJobsSchema.CITY);
        String state = text(posting, rec, DumJobsSchema.STATE);
        String raw = posting == null ? null : asText(posting.get(DumJobSourceAdapter.LOCATION_RAW));
        Counter counter = counters.computeIfAbsent(source.getSourceName(), name -> new Counter());
        counter.records.incrementAndGet();
        DumJobLocationResolver resolver = resolver();
        if (resolver == null) {
            // The library does not hold the taxonomy: pass the adapter's own values through rather than
            // lose them — they were published that way before this step existed.
            return passThrough(city, state);
        }
        DumJobLocationResolver.Resolution place = resolver.resolve(city, state, raw);
        Map<String, Object> contributions = new LinkedHashMap<>();
        put(contributions, DumJobsSchema.CITY, place.city());
        put(contributions, DumJobsSchema.STATE, place.uf());
        if (place.remote()) {
            contributions.put(DumJobsSchema.REMOTE, DumJobNormalizer.REMOTE);
        }
        if (place.unresolved() != null) {
            counter.unresolved.incrementAndGet();
            if (source instanceof DumJobsStructuredSource jobs) {
                jobs.annotate(rec.id(), DumJobSourceAdapter.LOCATION_UNRESOLVED, place.unresolved());
            }
            log.debug("Job '{}' in source '{}': place '{}' not resolved", rec.id(),
                    source.getSourceName(), place.unresolved());
        } else if (!contributions.isEmpty()) {
            counter.resolved.incrementAndGet();
        }
        return contributions;
    }

    /** This source's counts since the last {@link #logSummary} / {@link #reset}, without clearing them. */
    public Summary summary(String sourceName) {
        Counter counter = counters.get(sourceName);
        return counter == null ? new Summary(0, 0, 0) : counter.snapshot();
    }

    /** Forget this source's counts — called when a run starts. */
    public void reset(String sourceName) {
        counters.remove(sourceName);
    }

    /**
     * Log one summary line for the source's run and clear its counts.
     *
     * <p>
     * The line is the only place the unresolved count goes: D96 (a structured source cannot report
     * notes about its run) means the run record has nowhere to carry it yet. Once D96 lands, this count
     * belongs on the run record.
     */
    public Summary logSummary(String sourceName) {
        Counter counter = counters.remove(sourceName);
        Summary summary = counter == null ? new Summary(0, 0, 0) : counter.snapshot();
        if (summary.unresolved() > 0) {
            log.warn("Job source '{}' locations: {} record(s), {} resolved, {} unresolved "
                    + "(kept raw under '{}')", sourceName, summary.records(), summary.resolved(),
                    summary.unresolved(), DumJobSourceAdapter.LOCATION_UNRESOLVED);
        } else {
            log.info("Job source '{}' locations: {} record(s), {} resolved, 0 unresolved", sourceName,
                    summary.records(), summary.resolved());
        }
        return summary;
    }

    /** The resolver for the taxonomy version the library answers now; rebuilt only on a bump. */
    private DumJobLocationResolver resolver() {
        DumTaxonomy taxonomy = library.taxonomy(taxonomyRef).orElse(null);
        if (taxonomy == null) {
            return null;
        }
        Cached current = cached;
        if (current == null || current.taxonomy() != taxonomy) {
            current = new Cached(taxonomy, DumJobLocationResolver.of(taxonomy));
            cached = current;
        }
        return current.resolver();
    }

    private static Map<String, Object> passThrough(String city, String state) {
        Map<String, Object> contributions = new LinkedHashMap<>();
        put(contributions, DumJobsSchema.CITY, city);
        put(contributions, DumJobsSchema.STATE, DumJobNormalizer.uf(state));
        return contributions;
    }

    /** The posting's value for {@code field}, else the record's (a source that is not a job source). */
    private static String text(Map<String, Object> posting, DumStructuredRecord rec, String field) {
        Object value = posting != null ? posting.get(field) : rec.values().get(field);
        return asText(value);
    }

    private static String asText(Object value) {
        if (value instanceof java.util.Collection<?> values) {
            value = values.isEmpty() ? null : values.iterator().next();
        }
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static void put(Map<String, Object> contributions, String field, String value) {
        if (value != null && !value.isBlank()) {
            contributions.put(field, value);
        }
    }
}
