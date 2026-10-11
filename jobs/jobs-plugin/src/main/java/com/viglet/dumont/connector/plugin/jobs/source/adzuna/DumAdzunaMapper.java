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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;

/**
 * Maps one Adzuna search result to a job record keyed by {@link DumJobsSchema#FIELDS} (trabalharei
 * <strong>TRA15</strong>). Pure. A value Adzuna does not state is left out — never a placeholder:
 *
 * <ul>
 * <li>{@code state}/{@code city} come from {@code location.area} ({@code ["Brasil", state, city,
 * ...]}): the state is the UF of {@code area[1]}, the city is the last element when it sits below
 * the state; an unknown state leaves both out.</li>
 * <li>Salaries are stated only when {@code salary_is_predicted} is not {@code "1"} (Adzuna's own
 * estimate is not the employer's offer). Adzuna Brazil salaries are annual, so they are divided by 12
 * and published with the original period, {@code salaryPeriod = "ano"}; the salary enricher (TRA21)
 * fills the band from the monthly minimum.</li>
 * <li>{@code contract} maps {@code contract_type} only ({@code permanent} &rarr; {@code clt},
 * {@code contract} &rarr; {@code pj}). {@code contract_time} (full/part time) names no contract kind
 * in the site's vocabulary, so it never sets one.</li>
 * <li>{@code remote} is {@code remoto} when the title, description or location says so, else
 * absent; {@code occupation}, {@code seniority}, {@code abstract} and {@code expiresAt} are left
 * for later stages.</li>
 * <li>{@code applyUrl} is Adzuna's {@code redirect_url} exactly as received.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumAdzunaMapper {

    /** The {@code source} value and the id prefix. */
    public static final String SOURCE = "adzuna";

    /** The {@code salaryPeriod} of every Adzuna salary: Adzuna Brazil quotes yearly amounts. */
    static final String YEARLY = "ano";

    private DumAdzunaMapper() {
    }

    /** The record for {@code result}, or an empty map when it has no Adzuna id. */
    public static Map<String, Object> map(JSONObject result, Instant now, String siteUrl) {
        Map<String, Object> job = new LinkedHashMap<>();
        String partnerId = text(result, "id");
        if (partnerId == null) {
            return job;
        }
        String id = SOURCE + "-" + partnerId;
        String title = DumJobNormalizer.plainText(text(result, "title"));
        String description = DumJobNormalizer.plainText(text(result, "description"));
        JSONObject location = result.optJSONObject("location");

        put(job, DumJobsSchema.ID, id);
        put(job, DumJobsSchema.TITLE, title);
        put(job, DumJobsSchema.TEXT, description);
        put(job, DumJobsSchema.COMPANY, text(result.optJSONObject("company"), "display_name"));
        putPlace(job, location);
        put(job, DumJobsSchema.REMOTE, DumJobNormalizer.remote(title, description,
                text(location, "display_name"), areaText(location)));
        put(job, DumJobsSchema.CONTRACT, contract(text(result, "contract_type")));
        putSalary(job, result);
        put(job, DumJobsSchema.SOURCE, SOURCE);
        put(job, DumJobsSchema.URL, DumJobNormalizer.siteUrl(siteUrl, title, id));
        put(job, DumJobsSchema.APPLY_URL, rawText(result, "redirect_url"));
        put(job, DumJobsSchema.PUBLISHED_AT, text(result, "created"));
        // Adzuna exposes no edit date. Using the posting's own date (not the run's clock)
        // keeps a re-fetched, unchanged posting on the same checksum, so it is not
        // re-sent to Turing every run.
        put(job, DumJobsSchema.MODIFICATION_DATE, text(result, "created"));
        return job;
    }

    /** {@code permanent} &rarr; {@code clt}, {@code contract} &rarr; {@code pj}; anything else absent. */
    static String contract(String contractType) {
        if (contractType == null) {
            return null;
        }
        return switch (contractType.trim().toLowerCase(Locale.ROOT)) {
            case "permanent" -> "clt";
            case "contract" -> "pj";
            default -> null;
        };
    }

    private static void putPlace(Map<String, Object> job, JSONObject location) {
        JSONArray area = location == null ? null : location.optJSONArray("area");
        if (area == null || area.length() < 2) {
            return;
        }
        String uf = DumJobNormalizer.uf(area.optString(1, null));
        if (uf == null) {
            return;
        }
        put(job, DumJobsSchema.STATE, uf);
        if (area.length() > 2) {
            put(job, DumJobsSchema.CITY, blankToNull(area.optString(area.length() - 1, null)));
        }
    }

    private static void putSalary(Map<String, Object> job, JSONObject result) {
        if ("1".equals(text(result, "salary_is_predicted"))) {
            return;
        }
        Double min = positiveNumber(result, "salary_min");
        Double max = positiveNumber(result, "salary_max");
        if (min == null && max == null) {
            return;
        }
        Double monthlyMin = min == null ? null : DumJobNormalizer.annualToMonthly(min);
        Double monthlyMax = max == null ? null : DumJobNormalizer.annualToMonthly(max);
        put(job, DumJobsSchema.SALARY_MIN, monthlyMin);
        put(job, DumJobsSchema.SALARY_MAX, monthlyMax);
        put(job, DumJobsSchema.SALARY_PERIOD, YEARLY);
    }

    private static Double positiveNumber(JSONObject result, String key) {
        Object value = result.opt(key);
        double number;
        if (value instanceof Number n) {
            number = n.doubleValue();
        } else if (value instanceof String s && !s.isBlank()) {
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

    private static String areaText(JSONObject location) {
        JSONArray area = location == null ? null : location.optJSONArray("area");
        if (area == null) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < area.length(); i++) {
            text.append(area.optString(i, "")).append(' ');
        }
        return text.toString();
    }

    /** A trimmed string value, or {@code null} when missing, JSON null or blank. */
    private static String text(JSONObject object, String key) {
        String raw = rawText(object, key);
        return raw == null ? null : blankToNull(raw.trim());
    }

    /** The string value exactly as sent, or {@code null} when missing, JSON null or blank. */
    private static String rawText(JSONObject object, String key) {
        if (object == null || !object.has(key) || object.isNull(key)) {
            return null;
        }
        String value = object.get(key).toString();
        return value.isBlank() ? null : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void put(Map<String, Object> job, String field, Object value) {
        if (value != null) {
            job.put(field, value);
        }
    }
}
