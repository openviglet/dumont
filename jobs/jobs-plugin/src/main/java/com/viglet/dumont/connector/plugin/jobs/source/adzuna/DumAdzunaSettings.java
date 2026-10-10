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

package com.viglet.dumont.connector.plugin.jobs.source.adzuna;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;

/**
 * The Adzuna keys a source may set in its {@code settings} JSON (trabalharei <strong>TRA15</strong>).
 * Every key is optional:
 *
 * <pre>
 * {
 *   "maxDaysOld": 30,          // Adzuna max_days_old on every query
 *   "maxPages": 5,             // pages (of 50) one query may read per run
 *   "categories": ["it-jobs"], // category tags rotated one per run; [] turns rotation off
 *   "what": "vaga",            // keywords added to the category queries only
 *   "siteUrl": "https://trabalharei.com.br/vaga/"  // base of the job-page url
 * }
 * </pre>
 *
 * @param maxDaysOld Adzuna {@code max_days_old}
 * @param maxPages   the most pages one query reads in a run
 * @param categories the category-rotation list, one slot queried per run
 * @param what       keywords for the category queries, or {@code null}
 * @param siteUrl    base of the job detail page
 */
public record DumAdzunaSettings(int maxDaysOld, int maxPages, List<String> categories, String what,
        String siteUrl) {

    public static final int DEFAULT_MAX_DAYS_OLD = 30;
    public static final int DEFAULT_MAX_PAGES = 5;

    /**
     * Adzuna Brazil category tags rotated when a source sets none — broad, high-volume families, so
     * the rotation reaches postings the newest-first pass is too short to page back to.
     */
    public static final List<String> DEFAULT_CATEGORIES = List.of(
            "it-jobs",
            "sales-jobs",
            "admin-jobs",
            "customer-services-jobs",
            "healthcare-nursing-jobs",
            "engineering-jobs",
            "accounting-finance-jobs",
            "logistics-warehouse-jobs",
            "teaching-jobs",
            "hospitality-catering-jobs");

    public DumAdzunaSettings {
        categories = categories == null ? List.of() : List.copyOf(categories);
    }

    /** The defaults: every key unset. */
    public static DumAdzunaSettings defaults() {
        return new DumAdzunaSettings(DEFAULT_MAX_DAYS_OLD, DEFAULT_MAX_PAGES, DEFAULT_CATEGORIES, null,
                DumJobNormalizer.DEFAULT_SITE_URL);
    }

    /**
     * Read a source's settings JSON. A blank value means all defaults; malformed JSON throws, so a
     * typo in the configuration fails the run instead of silently running with the defaults.
     */
    public static DumAdzunaSettings parse(String json) {
        if (json == null || json.isBlank()) {
            return defaults();
        }
        JSONObject settings;
        try {
            settings = new JSONObject(json);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Adzuna source settings are not a JSON object: "
                    + e.getMessage(), e);
        }
        int maxDaysOld = positive(settings.optInt("maxDaysOld", DEFAULT_MAX_DAYS_OLD),
                DEFAULT_MAX_DAYS_OLD);
        int maxPages = positive(settings.optInt("maxPages", DEFAULT_MAX_PAGES), DEFAULT_MAX_PAGES);
        List<String> categories = DEFAULT_CATEGORIES;
        JSONArray configured = settings.optJSONArray("categories");
        if (configured != null) {
            categories = new ArrayList<>();
            for (int i = 0; i < configured.length(); i++) {
                String tag = configured.optString(i, "").trim();
                if (!tag.isEmpty()) {
                    categories.add(tag);
                }
            }
        }
        String what = settings.optString("what", "").trim();
        String siteUrl = settings.optString("siteUrl", "").trim();
        return new DumAdzunaSettings(maxDaysOld, maxPages, categories, what.isEmpty() ? null : what,
                siteUrl.isEmpty() ? DumJobNormalizer.DEFAULT_SITE_URL : siteUrl);
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
