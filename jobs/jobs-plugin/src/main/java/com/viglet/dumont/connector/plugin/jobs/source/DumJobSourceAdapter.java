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
}
