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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.json.JSONObject;

import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobSourceAdapter;
import com.viglet.dumont.connector.plugin.jobs.source.contract.DumJobContractParser;

/**
 * Maps one Jooble search result to a job record keyed by {@link DumJobsSchema#FIELDS} (trabalharei
 * <strong>TRA16</strong>). Pure. A value Jooble does not state is left out — never a placeholder:
 *
 * <ul>
 * <li>{@code id} is {@code jooble-<id>}; a result without an id falls back to a stable hash of its
 * {@code link} ({@code jooble-h<16 hex>}), and one with neither is dropped.</li>
 * <li>{@code state}/{@code city} come from the free-text {@code location} ({@code "Campinas, SP"},
 * {@code "São Paulo - SP"}): the state is the last part that names a UF, the city the part just
 * before it. A location that names no UF ({@code "Brasil"}) or says the work is remote
 * ({@code "Remoto"}, {@code "Home office"}) sets neither. The text itself is kept, as received, under
 * {@code locationRaw} for the location enricher (TRA20), which writes the canonical city and UF.</li>
 * <li>{@code contract} maps {@code type} only when it names exactly one kind of the site's vocabulary
 * ({@link DumJobContractParser}); full-time/part-time never sets one. The contract enricher (TRA21)
 * takes it as the partner's value and falls back to the title and snippet.</li>
 * <li>{@code salary} is free text, so it is kept as received under {@code salaryRaw} for the salary
 * enricher (TRA21), which parses it; the mapper sets no salary field.</li>
 * <li>{@code applyUrl} is Jooble's {@code link} exactly as received (Jooble pays per click on it).</li>
 * <li>{@code publishedAt} and {@code modificationDate} are both {@code updated} as Jooble sends it
 * (ISO-8601, usually without a zone — the structured source reads a zone-less time as UTC): never the
 * run's clock, so an unchanged re-fetch keeps its checksum.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumJoobleMapper {

    /** The {@code source} value and the id prefix. */
    public static final String SOURCE = "jooble";

    private static final Pattern PLACE_SEPARATORS = Pattern.compile("\\s*[,/]\\s*|\\s+[-–]\\s+");
    private static final Set<String> COUNTRY = Set.of("brasil", "brazil", "br");

    private static final Pattern SPACES = Pattern.compile("\\s+");

    private DumJoobleMapper() {
    }

    /** The record for {@code result}, or an empty map when it has neither an id nor a link. */
    public static Map<String, Object> map(JSONObject result, String siteUrl) {
        Map<String, Object> job = new LinkedHashMap<>();
        String link = rawText(result, "link");
        String id = id(text(result, "id"), link);
        if (id == null) {
            return job;
        }
        String title = plainText(text(result, "title"));
        String snippet = plainText(text(result, "snippet"));
        String location = text(result, "location");
        String updated = text(result, "updated");

        put(job, DumJobsSchema.ID, id);
        put(job, DumJobsSchema.TITLE, title);
        put(job, DumJobsSchema.TEXT, snippet);
        put(job, DumJobsSchema.COMPANY, plainText(text(result, "company")));
        boolean remoteLocation = DumJobNormalizer.remote(location) != null;
        if (!remoteLocation) {
            putPlace(job, location);
        }
        // The text as received, for the location enricher (TRA20) — not a schema field.
        put(job, DumJobSourceAdapter.LOCATION_RAW, location);
        put(job, DumJobsSchema.REMOTE, DumJobNormalizer.remote(title, snippet, location));
        put(job, DumJobsSchema.CONTRACT, contract(text(result, "type")));
        // The text as received, for the salary enricher (TRA21) — not a schema field.
        put(job, DumJobSourceAdapter.SALARY_RAW, text(result, "salary"));
        put(job, DumJobsSchema.SOURCE, SOURCE);
        put(job, DumJobsSchema.URL, DumJobNormalizer.siteUrl(siteUrl, title, id));
        put(job, DumJobsSchema.APPLY_URL, link);
        put(job, DumJobsSchema.PUBLISHED_AT, updated);
        // Jooble's own date, never the run clock: an unchanged posting keeps its checksum.
        put(job, DumJobsSchema.MODIFICATION_DATE, updated);
        return job;
    }

    /** {@code jooble-<partnerId>}, else {@code jooble-h<hash of link>}, else {@code null}. */
    static String id(String partnerId, String link) {
        if (partnerId != null) {
            return SOURCE + "-" + partnerId;
        }
        return link == null ? null : SOURCE + "-h" + DumJobNormalizer.shortHash(link);
    }

    /**
     * The contract a Jooble {@code type} names, when it names exactly one kind of the site's
     * vocabulary; {@code null} for none, several, or only a working-time (full/part-time) value.
     */
    static String contract(String type) {
        return DumJobContractParser.parse(type).orElse(null);
    }

    /** Set {@code state} (and {@code city} when a part precedes the UF) from a free-text location. */
    static void putPlace(Map<String, Object> job, String location) {
        if (location == null) {
            return;
        }
        List<String> parts = new ArrayList<>();
        for (String part : PLACE_SEPARATORS.split(location.trim())) {
            if (!part.isBlank() && !COUNTRY.contains(DumJobNormalizer.fold(part))) {
                parts.add(part.trim());
            }
        }
        for (int i = parts.size() - 1; i >= 0; i--) {
            String uf = DumJobNormalizer.uf(parts.get(i));
            if (uf != null) {
                put(job, DumJobsSchema.STATE, uf);
                if (i > 0) {
                    put(job, DumJobsSchema.CITY, parts.get(i - 1));
                }
                return;
            }
        }
    }

    /**
     * Tags stripped (by {@link DumJobNormalizer#plainText(String)}) and the few entities Jooble
     * snippets carry decoded; {@code null} when nothing is left.
     */
    static String plainText(String html) {
        String plain = DumJobNormalizer.plainText(html);
        if (plain == null) {
            return null;
        }
        String decoded = plain.replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
        String collapsed = SPACES.matcher(decoded).replaceAll(" ").trim();
        return collapsed.isEmpty() ? null : collapsed;
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
