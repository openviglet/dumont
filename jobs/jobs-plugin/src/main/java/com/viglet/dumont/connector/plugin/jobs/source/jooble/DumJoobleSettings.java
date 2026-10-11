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

package com.viglet.dumont.connector.plugin.jobs.source.jooble;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobRequest;

/**
 * The Jooble keys a source may set in its {@code settings} JSON (trabalharei <strong>TRA16</strong>).
 * Every key is optional:
 *
 * <pre>
 * {
 *   "method": "POST",                 // POST (Jooble's documented form, JSON body) or GET (query string)
 *   "keywords": ["emprego", "vagas"], // keyword queries rotated one per run; Jooble needs keywords
 *   "location": "Brasil",             // Jooble location on every query
 *   "radius": 40,                     // Jooble radius (km), sent only when set
 *   "salary": 2000,                   // Jooble minimum salary filter, sent only when set
 *   "maxPages": 5,                    // pages one run may read
 *   "siteUrl": "https://trabalharei.com.br/vaga/"  // base of the job-page url
 * }
 * </pre>
 *
 * @param method   {@code POST} or {@code GET}
 * @param keywords the keyword-rotation list, one slot queried per run (never empty)
 * @param location the Jooble location
 * @param radius   the Jooble radius, or {@code null}
 * @param salary   the Jooble salary filter, or {@code null}
 * @param maxPages the most pages one run reads
 * @param siteUrl  base of the job detail page
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record DumJoobleSettings(String method, List<String> keywords, String location, Integer radius,
        Integer salary, int maxPages, String siteUrl) {

    public static final String DEFAULT_METHOD = DumJobRequest.POST;
    public static final String DEFAULT_LOCATION = "Brasil";
    public static final int DEFAULT_MAX_PAGES = 5;

    /**
     * Broad pt-BR keyword queries rotated when a source sets none: Jooble refuses a search without
     * keywords, so the rotation stands in for an unfiltered newest-first pass.
     */
    public static final List<String> DEFAULT_KEYWORDS = List.of(
            "emprego",
            "vagas",
            "estágio",
            "auxiliar",
            "vendedor",
            "motorista",
            "desenvolvedor",
            "atendente");

    public DumJoobleSettings {
        keywords = keywords == null || keywords.isEmpty() ? DEFAULT_KEYWORDS : List.copyOf(keywords);
    }

    /** The defaults: every key unset. */
    public static DumJoobleSettings defaults() {
        return new DumJoobleSettings(DEFAULT_METHOD, DEFAULT_KEYWORDS, DEFAULT_LOCATION, null, null,
                DEFAULT_MAX_PAGES, DumJobNormalizer.DEFAULT_SITE_URL);
    }

    /**
     * Read a source's settings JSON. A blank value means all defaults; malformed JSON or an unknown
     * {@code method} throws, so a typo in the configuration fails the run instead of silently running
     * with the defaults. An empty {@code keywords} list falls back to the defaults (Jooble cannot
     * search without keywords).
     */
    public static DumJoobleSettings parse(String json) {
        if (json == null || json.isBlank()) {
            return defaults();
        }
        JSONObject settings;
        try {
            settings = new JSONObject(json);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Jooble source settings are not a JSON object: "
                    + e.getMessage(), e);
        }
        String method = settings.optString("method", "").trim().toUpperCase(Locale.ROOT);
        if (method.isEmpty()) {
            method = DEFAULT_METHOD;
        } else if (!DumJobRequest.GET.equals(method) && !DumJobRequest.POST.equals(method)) {
            throw new IllegalArgumentException(
                    "Jooble source settings: method must be POST or GET, not '" + method + "'");
        }
        List<String> keywords = new ArrayList<>();
        JSONArray configured = settings.optJSONArray("keywords");
        if (configured != null) {
            for (int i = 0; i < configured.length(); i++) {
                String keyword = configured.optString(i, "").trim();
                if (!keyword.isEmpty()) {
                    keywords.add(keyword);
                }
            }
        }
        String location = settings.optString("location", "").trim();
        int maxPages = settings.optInt("maxPages", DEFAULT_MAX_PAGES);
        String siteUrl = settings.optString("siteUrl", "").trim();
        return new DumJoobleSettings(method, keywords,
                location.isEmpty() ? DEFAULT_LOCATION : location,
                positiveOrNull(settings, "radius"), positiveOrNull(settings, "salary"),
                maxPages > 0 ? maxPages : DEFAULT_MAX_PAGES,
                siteUrl.isEmpty() ? DumJobNormalizer.DEFAULT_SITE_URL : siteUrl);
    }

    private static Integer positiveOrNull(JSONObject settings, String key) {
        int value = settings.optInt(key, 0);
        return value > 0 ? value : null;
    }
}
