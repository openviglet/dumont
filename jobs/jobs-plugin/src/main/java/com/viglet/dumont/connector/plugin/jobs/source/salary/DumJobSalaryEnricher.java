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

package com.viglet.dumont.connector.plugin.jobs.source.salary;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnricher;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumSourced;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumTransient;
import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobSourceAdapter;
import com.viglet.dumont.connector.plugin.jobs.source.salary.DumJobSalaryParser.Result;
import com.viglet.dumont.connector.plugin.jobs.source.salary.DumJobSalaryParser.Status;

import lombok.extern.slf4j.Slf4j;

/**
 * The single canonical salary step of every job record (trabalharei <strong>TRA21</strong>): a D04
 * {@link DumEnricher} that writes {@code salaryMin}, {@code salaryMax} (monthly BRL),
 * {@code salaryPeriod} and {@code salaryBand}.
 *
 * <ul>
 * <li><strong>Numbers from the partner</strong> (Adzuna, JSearch: the adapter already put monthly
 * amounts in {@code salaryMin}/{@code salaryMax}) are never re-parsed: they pass through as they are,
 * with the adapter's period, and the band is filled from the minimum when the posting has none.</li>
 * <li><strong>Text</strong> an adapter keeps under {@link DumJobSourceAdapter#SALARY_RAW} (Jooble) is
 * read by {@link DumJobSalaryParser}. Text that states no amount ("a combinar") yields nothing; text
 * with an amount the parser cannot read is kept raw in the record transient
 * {@link DumJobSourceAdapter#SALARY_UNPARSED} (never indexed) and counted per source.</li>
 * </ul>
 *
 * <p>
 * Like the location step it reads the adapter's salary values and {@code salaryRaw} from the record
 * transients (D97, D117), and {@code extract} leaves the four salary fields off the record (D98: an
 * enricher cannot clear a value), so this step is the only writer.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public final class DumJobSalaryEnricher implements DumEnricher {

    /** The enricher name, also the provenance tag of the fields it writes. */
    public static final String NAME = "job-salary";

    /** The salary bands a posting may already carry (trabalharei {@code SALARY_BANDS}). */
    public static final Set<String> BANDS = Set.of("ate-1.5k", "1.5k-3k", "3k-5k", "5k-8k", "8k-12k",
            "12k+");

    /** Per-source counts of one run: postings with partner numbers, parsed text, unparsed text. */
    public record Summary(int records, int structured, int parsed, int unparsed) {
    }

    private static final class Counter {
        final AtomicInteger records = new AtomicInteger();
        final AtomicInteger structured = new AtomicInteger();
        final AtomicInteger parsed = new AtomicInteger();
        final AtomicInteger unparsed = new AtomicInteger();

        Summary snapshot() {
            return new Summary(records.get(), structured.get(), parsed.get(), unparsed.get());
        }
    }

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord rec) {
        Map<String, Object> values = inputs(rec);
        Counter counter = counters.computeIfAbsent(source.getSourceName(), name -> new Counter());
        counter.records.incrementAndGet();
        Map<String, Object> contributions = new LinkedHashMap<>();

        Double min = positive(values.get(DumJobsSchema.SALARY_MIN));
        Double max = positive(values.get(DumJobsSchema.SALARY_MAX));
        if (min != null || max != null) {
            counter.structured.incrementAndGet();
            // D101: each value names the partner field it came from; a band the partner did not send
            // is derived from the minimum.
            put(contributions, DumJobsSchema.SALARY_MIN, min, DumJobsSchema.SALARY_MIN);
            put(contributions, DumJobsSchema.SALARY_MAX, max, DumJobsSchema.SALARY_MAX);
            put(contributions, DumJobsSchema.SALARY_PERIOD,
                    DumJobSalaryParser.periodValue(single(values.get(DumJobsSchema.SALARY_PERIOD))),
                    DumJobsSchema.SALARY_PERIOD);
            String band = band(single(values.get(DumJobsSchema.SALARY_BAND)));
            put(contributions, DumJobsSchema.SALARY_BAND,
                    band != null ? band : DumJobNormalizer.salaryBand(min),
                    band != null ? DumJobsSchema.SALARY_BAND : DumJobsSchema.SALARY_MIN);
            return contributions;
        }
        String raw = text(values.get(DumJobSourceAdapter.SALARY_RAW));
        if (raw == null) {
            return contributions;
        }
        Result result = DumJobSalaryParser.parse(raw);
        if (result.status() == Status.UNPARSED) {
            counter.unparsed.incrementAndGet();
            contributions.put(DumJobSourceAdapter.SALARY_UNPARSED, DumTransient.of(raw));
            log.debug("Job '{}' in source '{}': salary '{}' not parsed", rec.id(), source.getSourceName(),
                    raw);
            return contributions;
        }
        if (result.status() == Status.PARSED) {
            counter.parsed.incrementAndGet();
            DumJobSalaryParser.Salary salary = result.salary();
            put(contributions, DumJobsSchema.SALARY_MIN, salary.min(), DumJobSourceAdapter.SALARY_RAW);
            put(contributions, DumJobsSchema.SALARY_MAX, salary.max(), DumJobSourceAdapter.SALARY_RAW);
            put(contributions, DumJobsSchema.SALARY_PERIOD, salary.period(), DumJobSourceAdapter.SALARY_RAW);
            put(contributions, DumJobsSchema.SALARY_BAND, salary.band(), DumJobSourceAdapter.SALARY_RAW);
        }
        return contributions;
    }

    /** The record's values with the adapter's own (its transients, D117) over them. */
    private static Map<String, Object> inputs(DumStructuredRecord rec) {
        Map<String, Object> values = new HashMap<>(rec.values());
        values.putAll(rec.transients());
        return values;
    }

    /**
     * Whether {@link #enrich} would publish a salary for this adapter posting — partner numbers, or a
     * {@link DumJobSourceAdapter#SALARY_RAW} text the parser reads — without counting or annotating it
     * (trabalharei <strong>TRA23</strong>: the duplicate a run keeps is the one with a salary).
     */
    public static boolean hasSalary(Map<String, Object> posting) {
        if (posting == null) {
            return false;
        }
        if (positive(posting.get(DumJobsSchema.SALARY_MIN)) != null
                || positive(posting.get(DumJobsSchema.SALARY_MAX)) != null) {
            return true;
        }
        String raw = text(posting.get(DumJobSourceAdapter.SALARY_RAW));
        return raw != null && DumJobSalaryParser.parse(raw).status() == Status.PARSED;
    }

    /** This source's counts since the last {@link #logSummary} / {@link #reset}, without clearing them. */
    public Summary summary(String sourceName) {
        Counter counter = counters.get(sourceName);
        return counter == null ? new Summary(0, 0, 0, 0) : counter.snapshot();
    }

    /** Forget this source's counts — called when a run starts. */
    public void reset(String sourceName) {
        counters.remove(sourceName);
    }

    /**
     * Log one summary line for the source's run and clear its counts (D100: enrichers have no
     * end-of-run hook, so the plugin calls this).
     */
    public Summary logSummary(String sourceName) {
        Counter counter = counters.remove(sourceName);
        Summary summary = counter == null ? new Summary(0, 0, 0, 0) : counter.snapshot();
        if (summary.unparsed() > 0) {
            log.warn("Job source '{}' salaries: {} record(s), {} from partner numbers, {} parsed, {} "
                    + "unparsed (kept raw under '{}')", sourceName, summary.records(), summary.structured(),
                    summary.parsed(), summary.unparsed(), DumJobSourceAdapter.SALARY_UNPARSED);
        } else {
            log.info("Job source '{}' salaries: {} record(s), {} from partner numbers, {} parsed, 0 "
                    + "unparsed", sourceName, summary.records(), summary.structured(), summary.parsed());
        }
        return summary;
    }

    /** A finite, positive number from a number or numeric text; {@code null} otherwise. */
    static Double positive(Object value) {
        Object one = single(value);
        double number;
        if (one instanceof Number n) {
            number = n.doubleValue();
        } else if (one instanceof String s && !s.isBlank()) {
            try {
                number = Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            return null;
        }
        return Double.isFinite(number) && number > 0 ? number : null;
    }

    private static String band(Object value) {
        String text = text(value);
        return text != null && BANDS.contains(text) ? text : null;
    }

    private static Object single(Object value) {
        if (value instanceof Collection<?> values) {
            return values.isEmpty() ? null : values.iterator().next();
        }
        return value;
    }

    private static String text(Object value) {
        Object one = single(value);
        if (one == null) {
            return null;
        }
        String text = one.toString().trim();
        return text.isEmpty() ? null : text;
    }

    /** {@code value} for {@code field}, citing the input it was read from (D101). */
    private static void put(Map<String, Object> contributions, String field, Object value, String input) {
        if (value != null) {
            contributions.put(field, DumSourced.of(value, input));
        }
    }
}
