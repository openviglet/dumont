/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;

import com.viglet.dumont.connector.commons.spi.structured.coverage.DumFieldCoverage;

/**
 * Per-crawl field-coverage signal for a web-crawler source (roadmap <strong>D23</strong>).
 *
 * <p>
 * The web crawler stays the generic breadth link-spider the structured framework sits <em>above</em>
 * — it has no typed {@code discover()}/{@code extract(id)} contract and this does not give it one.
 * What it <em>does</em> declare is a set of {@code DumWCAttributeMapping} names: the fields it tries
 * to pull from every page. Treating those names as an <em>implicit</em> field set, this accumulator
 * records a per-page field-presence signal during the crawl and turns it into the same
 * {@link DumFieldCoverage} the structured sources feed to D15 — so a WC source reports per-field
 * coverage across crawls and a mapping that silently stops matching surfaces as a coverage drop
 * (drift), without pretending to be a structured source.
 *
 * <p>
 * The declared field set is seeded once up front so a mapping that matches <em>zero</em> pages this
 * run still reports 0% coverage (exactly the broken-mapping signal), rather than vanishing from the
 * report. {@code percent} is computed with the same {@code Math.round(present * 100f / total)} as
 * {@code DumCoverageMonitor}, so a WC snapshot is directly comparable across runs for drift.
 *
 * <p>
 * Thread-safe: the bounded worker pool (roadmap D08) records pages into it concurrently.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
final class DumWCCoverageAccumulator {

    /** Display type for an implicit WC field — the crawler declares no search-engine type. */
    private static final String FIELD_TYPE = "STRING";

    /** Declared mapping names, in declaration order, blanks and duplicates dropped. */
    private final Set<String> fields = new LinkedHashSet<>();
    private final ConcurrentMap<String, AtomicInteger> present = new ConcurrentHashMap<>();
    private final AtomicInteger documents = new AtomicInteger();

    DumWCCoverageAccumulator(Collection<String> declaredFields) {
        if (declaredFields != null) {
            declaredFields.stream().filter(StringUtils::isNotBlank).forEach(fields::add);
        }
        fields.forEach(field -> present.put(field, new AtomicInteger()));
    }

    /**
     * Record one indexed page: bump the document count and, for each declared field present on the
     * page, its present count. Keys not in the declared set are ignored — coverage is reported only
     * for fields the source actually mapped.
     */
    void recordPage(Set<String> presentFields) {
        documents.incrementAndGet();
        if (presentFields == null) {
            return;
        }
        presentFields.forEach(field -> {
            AtomicInteger counter = present.get(field);
            if (counter != null) {
                counter.incrementAndGet();
            }
        });
    }

    /** The number of pages indexed this run (the coverage denominator). */
    int documentCount() {
        return documents.get();
    }

    /** No declared mappings — there is nothing to measure coverage for. */
    boolean isEmpty() {
        return fields.isEmpty();
    }

    /** Per-field coverage for this run, in declaration order. */
    List<DumFieldCoverage> toFieldCoverage() {
        int total = documents.get();
        List<DumFieldCoverage> coverage = new ArrayList<>(fields.size());
        for (String field : fields) {
            int p = present.get(field).get();
            int percent = total == 0 ? 0 : Math.round(p * 100f / total);
            coverage.add(new DumFieldCoverage(field, FIELD_TYPE, false, p, total, percent));
        }
        return coverage;
    }
}
