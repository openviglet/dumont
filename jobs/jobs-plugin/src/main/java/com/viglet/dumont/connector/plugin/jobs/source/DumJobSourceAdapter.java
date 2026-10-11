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

package com.viglet.dumont.connector.plugin.jobs.source;

import java.util.List;
import java.util.Map;

import com.viglet.dumont.connector.plugin.jobs.source.DumJobCallBudget.Window;

import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;

/**
 * One partner job API (trabalharei <strong>TRA13</strong>). An implementation is a Spring bean; the
 * {@link DumJobsSourceProvider} picks it for every source whose {@code sourceType} equals
 * {@link #id()}.
 *
 * <p>
 * The contract an adapter keeps:
 * <ul>
 * <li>{@link #fetch} returns raw postings as maps keyed by {@link DumJobsSchema#FIELDS} names. A key
 * outside the schema is ignored; a missing, {@code null} or blank value stays absent (grounding) —
 * never fill a field with a placeholder.</li>
 * <li>{@code city} and {@code state} are what the partner wrote (split as far as the adapter can);
 * the location enricher turns them into the canonical IBGE city and UF (trabalharei
 * <strong>TRA20</strong>). An adapter whose partner sends one free-text place also puts it, as
 * received, under {@link #LOCATION_RAW} — a record key the enricher reads, not a schema field.</li>
 * <li>Salary, contract and seniority have one canonical step each (trabalharei <strong>TRA21</strong>).
 * An adapter sets {@code salaryMin}/{@code salaryMax} (monthly BRL) and {@code salaryPeriod} only
 * when the partner sends numbers, and otherwise puts the partner's salary text under
 * {@link #SALARY_RAW}; it never computes {@code salaryBand}. It sets {@code contract} only from a
 * partner field that names the contract kind, never from the title or description, and never sets
 * {@code seniority}: the enrichers read title and text.</li>
 * <li>Every map carries {@link DumJobsSchema#ID}, unique across every source publishing to the same
 * site — conventionally {@code <id()>-<partner id>} (e.g. {@code adzuna-4512}).</li>
 * <li>Before each partner request it calls {@link DumJobFetchContext#budget()}{@code .tryAcquire()},
 * and stops — returning what it already has — when the budget refuses.</li>
 * <li>Dates ({@code publishedAt}, {@code expiresAt}, {@code modificationDate}) are ISO-8601 strings
 * or {@code java.time} values.</li>
 * <li>State an adapter needs from one run to the next (a rotation slot, a paging token) goes in
 * {@code context.source().setCursor(...)}; the structured source saves it after the fetch returns.
 * Adapter-specific configuration is read from {@code context.source().getSettings()} (JSON).</li>
 * </ul>
 */
public interface DumJobSourceAdapter {

    /**
     * Record key for the partner's raw location text ({@code "Campinas, SP"}). Kept with the stored
     * posting and read by the location enricher; never a schema field, never indexed.
     */
    String LOCATION_RAW = "locationRaw";

    /**
     * Record key the location enricher sets to the raw place text it could not resolve. Never a schema
     * field, never indexed.
     */
    String LOCATION_UNRESOLVED = "locationUnresolved";

    /**
     * Record key for the partner's salary as free text ({@code "R$ 2.500 a R$ 3.000 por mês"}), set by
     * an adapter whose partner sends no numbers. Read by the salary enricher (trabalharei
     * <strong>TRA21</strong>); never a schema field, never indexed.
     */
    String SALARY_RAW = "salaryRaw";

    /**
     * Record key the salary enricher sets to the {@link #SALARY_RAW} text it could not read. Never a
     * schema field, never indexed.
     */
    String SALARY_UNPARSED = "salaryUnparsed";

    /** The source type this adapter serves: {@code adzuna}, {@code jooble}, {@code bne} or {@code jsearch}. */
    String id();

    /** Fetch the newest postings the budget allows. A failure throws; an empty list is a valid answer. */
    List<Map<String, Object>> fetch(DumJobFetchContext context);

    /**
     * The partner plan's call caps, used for every window a source leaves {@code null}. Empty (the
     * default) means a source without configured caps is uncapped.
     */
    default Map<Window, Integer> defaultCaps() {
        return Map.of();
    }

    /**
     * The rotation slot a source cursor points at, for an adapter that rotates one query per run
     * through a list of {@code size} entries; an unset or unreadable cursor starts at 0.
     */
    static int rotationSlot(String cursor, int size) {
        if (size <= 0 || cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.floorMod(Integer.parseInt(cursor.trim()), size);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
