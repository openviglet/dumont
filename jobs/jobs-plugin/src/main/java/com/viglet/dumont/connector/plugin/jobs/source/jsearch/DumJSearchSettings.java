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

package com.viglet.dumont.connector.plugin.jobs.source.jsearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;

/**
 * The JSearch keys a source may set in its {@code settings} JSON (trabalharei <strong>TRA19</strong>).
 * Every key is optional:
 *
 * <pre>
 * {
 *   "queries": ["vagas", "emprego"],   // search queries rotated one per run
 *   "datePosted": "week",              // JSearch date_posted: all, today, 3days, week, month
 *   "maxPages": 2,                     // pages one run may read — each page is a paid call
 *   "apiUrl": "https://jsearch.p.rapidapi.com/search",  // search endpoint (the source's apiUrl wins)
 *   "siteUrl": "https://trabalharei.com.br/vaga/"       // base of the job-page url
 * }
 * </pre>
 *
 * @param queries    the query-rotation list, one slot queried per run (never empty)
 * @param datePosted the JSearch {@code date_posted} filter
 * @param maxPages   the most pages (paid calls) one run reads
 * @param apiUrl     the search endpoint from the settings, or {@code null}
 * @param siteUrl    base of the job detail page
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record DumJSearchSettings(List<String> queries, String datePosted, int maxPages, String apiUrl,
        String siteUrl) {

    public static final String DEFAULT_DATE_POSTED = "week";
    /** Two pages: every page is a paid call, and a week of postings rarely needs more per query. */
    public static final int DEFAULT_MAX_PAGES = 2;
    /** The {@code date_posted} values JSearch accepts. */
    public static final Set<String> DATE_POSTED = Set.of("all", "today", "3days", "week", "month");

    /** A short, broad pt-BR rotation when a source sets none; JSearch needs a query. */
    public static final List<String> DEFAULT_QUERIES = List.of(
            "vagas",
            "emprego",
            "estágio",
            "vagas home office",
            "vagas em São Paulo",
            "vagas no Rio de Janeiro");

    public DumJSearchSettings {
        queries = queries == null || queries.isEmpty() ? DEFAULT_QUERIES : List.copyOf(queries);
    }

    /** The defaults: every key unset. */
    public static DumJSearchSettings defaults() {
        return new DumJSearchSettings(DEFAULT_QUERIES, DEFAULT_DATE_POSTED, DEFAULT_MAX_PAGES, null,
                DumJobNormalizer.DEFAULT_SITE_URL);
    }

    /**
     * Read a source's settings JSON. A blank value means all defaults; malformed JSON or an unknown
     * {@code datePosted} throws, so a typo fails the run before any paid call instead of silently
     * running with the defaults. An empty {@code queries} list falls back to the defaults.
     */
    public static DumJSearchSettings parse(String json) {
        if (json == null || json.isBlank()) {
            return defaults();
        }
        JSONObject settings;
        try {
            settings = new JSONObject(json);
        } catch (JSONException e) {
            throw new IllegalArgumentException("JSearch source settings are not a JSON object: "
                    + e.getMessage(), e);
        }
        List<String> queries = new ArrayList<>();
        JSONArray configured = settings.optJSONArray("queries");
        if (configured != null) {
            for (int i = 0; i < configured.length(); i++) {
                String query = configured.optString(i, "").trim();
                if (!query.isEmpty()) {
                    queries.add(query);
                }
            }
        }
        String datePosted = settings.optString("datePosted", "").trim().toLowerCase(Locale.ROOT);
        if (datePosted.isEmpty()) {
            datePosted = DEFAULT_DATE_POSTED;
        } else if (!DATE_POSTED.contains(datePosted)) {
            throw new IllegalArgumentException("JSearch source settings: datePosted must be one of "
                    + "all, today, 3days, week, month, not '" + datePosted + "'");
        }
        int maxPages = settings.optInt("maxPages", DEFAULT_MAX_PAGES);
        String apiUrl = settings.optString("apiUrl", "").trim();
        String siteUrl = settings.optString("siteUrl", "").trim();
        return new DumJSearchSettings(queries, datePosted,
                maxPages > 0 ? maxPages : DEFAULT_MAX_PAGES, apiUrl.isEmpty() ? null : apiUrl,
                siteUrl.isEmpty() ? DumJobNormalizer.DEFAULT_SITE_URL : siteUrl);
    }
}
