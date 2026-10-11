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

package com.viglet.dumont.connector.plugin.jobs.source.bne;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;

/**
 * The BNE keys a source may set in its {@code settings} JSON (trabalharei <strong>TRA18</strong>).
 * Every key is optional:
 *
 * <pre>
 * {
 *   "apiUrl": "https://plugin-api.vagas.bne.com.br/api/v1/Lists/Jobs", // the list endpoint
 *   "itemsPerPage": 50,           // BNE ItemsPerPage
 *   "maxPages": 5,                // pages one query may read
 *   "states": ["SP", "RJ"],       // UF rotation, one per run (default: all 27)
 *   "siteUrl": "https://trabalharei.com.br/vaga/"  // base of the job-page url
 * }
 * </pre>
 *
 * The source's own {@code apiUrl} field, when set, wins over {@code settings.apiUrl}.
 *
 * @param apiUrl       the list endpoint from the settings, or {@code null}
 * @param itemsPerPage postings asked per page
 * @param maxPages     the most pages one query reads
 * @param states       the UF rotation, one slot queried per run (never empty)
 * @param siteUrl      base of the job detail page
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record DumBneSettings(String apiUrl, int itemsPerPage, int maxPages, List<String> states,
        String siteUrl) {

    public static final int DEFAULT_ITEMS_PER_PAGE = 50;
    public static final int DEFAULT_MAX_PAGES = 5;

    public DumBneSettings {
        states = states == null || states.isEmpty() ? DumJobNormalizer.UFS : List.copyOf(states);
    }

    /** The defaults: every key unset. */
    public static DumBneSettings defaults() {
        return new DumBneSettings(null, DEFAULT_ITEMS_PER_PAGE, DEFAULT_MAX_PAGES, DumJobNormalizer.UFS,
                DumJobNormalizer.DEFAULT_SITE_URL);
    }

    /**
     * Read a source's settings JSON. A blank value means all defaults; malformed JSON or a
     * {@code states} entry that is not a UF throws, so a typo in the configuration fails the run
     * instead of silently querying something else. An empty {@code states} list means all 27.
     */
    public static DumBneSettings parse(String json) {
        if (json == null || json.isBlank()) {
            return defaults();
        }
        JSONObject settings;
        try {
            settings = new JSONObject(json);
        } catch (JSONException e) {
            throw new IllegalArgumentException("BNE source settings are not a JSON object: "
                    + e.getMessage(), e);
        }
        List<String> states = new ArrayList<>();
        JSONArray configured = settings.optJSONArray("states");
        if (configured != null) {
            for (int i = 0; i < configured.length(); i++) {
                String state = configured.optString(i, "").trim();
                if (state.isEmpty()) {
                    continue;
                }
                String uf = DumJobNormalizer.uf(state);
                if (uf == null) {
                    throw new IllegalArgumentException(
                            "BNE source settings: '" + state + "' in states is not a Brazilian UF");
                }
                if (!states.contains(uf)) {
                    states.add(uf);
                }
            }
        }
        String apiUrl = settings.optString("apiUrl", "").trim();
        int itemsPerPage = settings.optInt("itemsPerPage", DEFAULT_ITEMS_PER_PAGE);
        int maxPages = settings.optInt("maxPages", DEFAULT_MAX_PAGES);
        String siteUrl = settings.optString("siteUrl", "").trim();
        return new DumBneSettings(apiUrl.isEmpty() ? null : apiUrl,
                itemsPerPage > 0 ? itemsPerPage : DEFAULT_ITEMS_PER_PAGE,
                maxPages > 0 ? maxPages : DEFAULT_MAX_PAGES, states,
                siteUrl.isEmpty() ? DumJobNormalizer.DEFAULT_SITE_URL : siteUrl);
    }
}
