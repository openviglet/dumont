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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobSourceAdapter;

/**
 * Maps one JSearch search result to a job record keyed by {@link DumJobsSchema#FIELDS} (trabalharei
 * <strong>TRA19</strong>). Pure. A value JSearch does not state is left out — never a placeholder:
 *
 * <ul>
 * <li>{@code id} is {@code jsearch-<16 hex>}, the start of the SHA-256 of {@code job_id} (a long,
 * base64-ish token); a result without {@code job_id} is dropped.</li>
 * <li>Only Brazilian postings are kept: a result whose {@code job_country} is not {@code BR} (or is
 * missing) is dropped.</li>
 * <li>{@code state} is the UF of {@code job_state} ({@code "SP"} or {@code "São Paulo"});
 * {@code city} is {@code job_city}; {@code job_location} is kept as received under {@code locationRaw}
 * for the location enricher (TRA20), which writes the canonical city and UF.</li>
 * <li>{@code remote} is {@code remoto} only when {@code job_is_remote} is true.</li>
 * <li>{@code contract}: {@code CONTRACTOR} &rarr; {@code pj}, {@code INTERN} &rarr; {@code estagio};
 * {@code FULLTIME}/{@code PARTTIME} are working time and never set one. Read from
 * {@code job_employment_types} when sent, else {@code job_employment_type}; two different kinds
 * leave it out.</li>
 * <li>Salaries are stated only when {@code job_min_salary} or {@code job_max_salary} is present, the
 * period is {@code MONTH} (or {@code YEAR}, divided by 12) and {@code job_salary_currency}, when
 * sent, is {@code BRL}. {@code salaryPeriod} is the original period ({@code mes} or {@code ano}); the
 * salary enricher (TRA21) fills the band.</li>
 * <li>{@code applyUrl} is {@code job_apply_link} exactly as received.</li>
 * <li>{@code publishedAt} and {@code modificationDate} are both {@code job_posted_at_datetime_utc}
 * (else {@code job_posted_at_timestamp}): never the run's clock, so an unchanged re-fetch keeps its
 * checksum. {@code expiresAt} is {@code job_offer_expiration_datetime_utc} (else its timestamp).</li>
 * </ul>
 *
 * <p>
 * Every mapped field comes from the {@code /search} answer; nothing here needs {@code /job-details}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumJSearchMapper {

    /** The {@code source} value and the id prefix. */
    public static final String SOURCE = "jsearch";

    private static final Set<String> BRAZIL = Set.of("br", "bra", "brasil", "brazil");
    private static final Pattern NON_LETTERS = Pattern.compile("[^A-Z]");
    private static final Pattern TYPE_SEPARATORS = Pattern.compile("[,/;]|\\band\\b",
            Pattern.CASE_INSENSITIVE);

    private DumJSearchMapper() {
    }

    /**
     * The record for {@code result}, or an empty map when it has no {@code job_id} or is not a
     * Brazilian posting.
     */
    public static Map<String, Object> map(JSONObject result, String siteUrl) {
        Map<String, Object> job = new LinkedHashMap<>();
        String id = id(text(result, "job_id"));
        if (id == null || !isBrazil(text(result, "job_country"))) {
            return job;
        }
        String title = DumJobNormalizer.plainText(text(result, "job_title"));
        String published = date(result, "job_posted_at_datetime_utc", "job_posted_at_timestamp");

        put(job, DumJobsSchema.ID, id);
        put(job, DumJobsSchema.TITLE, title);
        put(job, DumJobsSchema.TEXT, DumJobNormalizer.plainText(text(result, "job_description")));
        put(job, DumJobsSchema.COMPANY, text(result, "employer_name"));
        put(job, DumJobsSchema.CITY, text(result, "job_city"));
        put(job, DumJobsSchema.STATE, DumJobNormalizer.uf(text(result, "job_state")));
        // The free-text place as received, for the location enricher (TRA20) — not a schema field.
        put(job, DumJobSourceAdapter.LOCATION_RAW, text(result, "job_location"));
        put(job, DumJobsSchema.REMOTE, isTrue(result.opt("job_is_remote")) ? DumJobNormalizer.REMOTE : null);
        put(job, DumJobsSchema.CONTRACT, contract(employmentTypes(result)));
        putSalary(job, result);
        put(job, DumJobsSchema.SOURCE, SOURCE);
        put(job, DumJobsSchema.URL, DumJobNormalizer.siteUrl(siteUrl, title, id));
        put(job, DumJobsSchema.APPLY_URL, rawText(result, "job_apply_link"));
        put(job, DumJobsSchema.PUBLISHED_AT, published);
        // JSearch exposes no edit date: the posting's own date (never the run clock) keeps an
        // unchanged re-fetch on the same checksum.
        put(job, DumJobsSchema.MODIFICATION_DATE, published);
        put(job, DumJobsSchema.EXPIRES_AT, date(result, "job_offer_expiration_datetime_utc",
                "job_offer_expiration_timestamp"));
        return job;
    }

    /** {@code jsearch-<first 16 hex of SHA-256(jobId)>}, or {@code null} without a job id. */
    static String id(String jobId) {
        return jobId == null ? null : SOURCE + "-" + DumJobNormalizer.shortHash(jobId);
    }

    static boolean isBrazil(String country) {
        return BRAZIL.contains(DumJobNormalizer.fold(country));
    }

    /**
     * The contract the employment types name: {@code pj} for a contractor, {@code estagio} for an
     * intern; {@code null} when none or two different kinds are named. Full/part time is working time
     * and names no contract kind.
     */
    static String contract(List<String> types) {
        Set<String> kinds = new HashSet<>();
        for (String type : types) {
            for (String part : TYPE_SEPARATORS.split(type)) {
                String code = NON_LETTERS.matcher(part.toUpperCase(Locale.ROOT)).replaceAll("");
                switch (code) {
                    case "CONTRACTOR" -> kinds.add("pj");
                    case "INTERN", "INTERNSHIP" -> kinds.add("estagio");
                    default -> {
                        // FULLTIME, PARTTIME, TEMPORARY, ...: no contract kind of the site's vocabulary
                    }
                }
            }
        }
        return kinds.size() == 1 ? kinds.iterator().next() : null;
    }

    /** {@code job_employment_types} when sent and not empty, else {@code job_employment_type}. */
    private static List<String> employmentTypes(JSONObject result) {
        List<String> types = new ArrayList<>();
        JSONArray array = result.optJSONArray("job_employment_types");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String type = array.optString(i, "").trim();
                if (!type.isEmpty()) {
                    types.add(type);
                }
            }
        }
        if (types.isEmpty()) {
            String type = text(result, "job_employment_type");
            if (type != null) {
                types.add(type);
            }
        }
        return types;
    }

    /** Monthly BRL salary fields when JSearch states a monthly or yearly BRL amount; nothing otherwise. */
    static void putSalary(Map<String, Object> job, JSONObject result) {
        String currency = text(result, "job_salary_currency");
        if (currency != null && !"BRL".equalsIgnoreCase(currency)) {
            return;
        }
        String period = text(result, "job_salary_period");
        if (period == null) {
            return;
        }
        String unit = period.toUpperCase(Locale.ROOT);
        if (!"MONTH".equals(unit) && !"YEAR".equals(unit)) {
            return;
        }
        boolean yearly = "YEAR".equals(unit);
        Double min = positiveNumber(result, "job_min_salary");
        Double max = positiveNumber(result, "job_max_salary");
        if (min == null && max == null || min != null && max != null && max < min) {
            return;
        }
        if (yearly) {
            min = min == null ? null : DumJobNormalizer.annualToMonthly(min);
            max = max == null ? null : DumJobNormalizer.annualToMonthly(max);
        }
        put(job, DumJobsSchema.SALARY_MIN, min);
        put(job, DumJobsSchema.SALARY_MAX, max);
        put(job, DumJobsSchema.SALARY_PERIOD, yearly ? "ano" : DumJobNormalizer.MONTHLY);
    }

    /** The ISO-8601 text of {@code isoKey}, else the epoch-seconds {@code timestampKey} as an instant. */
    private static String date(JSONObject result, String isoKey, String timestampKey) {
        String iso = text(result, isoKey);
        if (iso != null) {
            return iso;
        }
        Object seconds = result.opt(timestampKey);
        if (seconds instanceof Number n && n.longValue() > 0) {
            return Instant.ofEpochSecond(n.longValue()).toString();
        }
        return null;
    }

    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || value instanceof String s && "true".equalsIgnoreCase(s.trim());
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

    /** A trimmed string value, or {@code null} when missing, JSON null or blank. */
    private static String text(JSONObject object, String key) {
        String raw = rawText(object, key);
        return raw == null ? null : raw.trim();
    }

    /** The string value exactly as sent, or {@code null} when missing, JSON null or blank. */
    private static String rawText(JSONObject object, String key) {
        if (object == null || !object.has(key) || object.isNull(key)) {
            return null;
        }
        String value = object.get(key).toString();
        return value.isBlank() ? null : value;
    }

    private static void put(Map<String, Object> job, String field, Object value) {
        if (value != null) {
            job.put(field, value);
        }
    }
}
