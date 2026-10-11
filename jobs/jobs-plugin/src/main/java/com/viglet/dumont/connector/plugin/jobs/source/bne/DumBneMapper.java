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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.json.JSONObject;

import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;

/**
 * Maps one BNE {@code listVagas} entry to a job record keyed by {@link DumJobsSchema#FIELDS}
 * (trabalharei <strong>TRA18</strong>). Pure. BNE sends only {@code Id}, {@code Idf_Vaga},
 * {@code Title}, {@code Description}, {@code Area}, {@code City} and {@code State}, so most fields stay
 * absent — never a placeholder:
 *
 * <ul>
 * <li>{@code id} is {@code bne-<Id>}; an entry without an {@code Id} is dropped.</li>
 * <li>{@code text} is {@code Description} as plain text; an empty description leaves {@code text}
 * absent (the WordPress plugin's "<em>title</em> em <em>city/uf</em> área <em>area</em>" sentence
 * is invented content and is not built).</li>
 * <li>{@code state} is {@code State} when it names one of the 27 UFs; {@code city} is
 * {@code City}.</li>
 * <li>{@code remote} only when the title or description says so. BNE has no contract field, so
 * {@code contract} is left to the contract enricher (TRA21), which reads the title and
 * description.</li>
 * <li>{@code applyUrl} is the BNE job page built exactly as the {@code bne-vagas} plugin 1.0.0
 * builds it ({@link #applyUrl}); absent without an {@code Idf_Vaga}.</li>
 * <li>{@code publishedAt} and {@code modificationDate} are both the time the posting was first
 * fetched, to the second — BNE sends no date, and a stable value keeps an unchanged posting's
 * checksum.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumBneMapper {

    /** The {@code source} value and the id prefix. */
    public static final String SOURCE = "bne";

    static final String APPLY_BASE = "https://www.bne.com.br/vaga-de-emprego-na-area-";

    /** The accents the plugin's {@code TirarAcentos} replaces, and nothing else (ç stays). */
    private static final String[][] PLUGIN_ACCENTS = {
            { "áàãâä", "a" }, { "ÁÀÃÂÄ", "A" },
            { "éèêë", "e" }, { "ÉÈÊË", "E" },
            { "íìîï", "i" }, { "ÍÌÎÏ", "I" },
            { "óòõôö", "o" }, { "ÓÒÕÔÖ", "O" },
            { "úùûü", "u" }, { "ÚÙÛÜ", "U" },
            { "ñ", "n" }, { "Ñ", "N" } };

    /** The plugin's {@code preg_replace('/[ -]+/', '-', ...)}: ASCII spaces and hyphens only. */
    private static final Pattern SPACES_OR_HYPHENS = Pattern.compile("[ -]+");

    private DumBneMapper() {
    }

    /** {@code bne-<Id>}, or {@code null} when the entry has no {@code Id}. */
    public static String id(JSONObject vaga) {
        String id = text(vaga, "Id");
        return id == null ? null : SOURCE + "-" + id;
    }

    /**
     * The record for {@code vaga}, first fetched at {@code firstSeen}; an empty map when it has no
     * {@code Id}.
     */
    public static Map<String, Object> map(JSONObject vaga, String siteUrl, Instant firstSeen) {
        Map<String, Object> job = new LinkedHashMap<>();
        String id = id(vaga);
        if (id == null) {
            return job;
        }
        String title = DumJobNormalizer.plainText(text(vaga, "Title"));
        String description = DumJobNormalizer.plainText(text(vaga, "Description"));

        put(job, DumJobsSchema.ID, id);
        put(job, DumJobsSchema.TITLE, title);
        put(job, DumJobsSchema.TEXT, description);
        put(job, DumJobsSchema.CITY, text(vaga, "City"));
        put(job, DumJobsSchema.STATE, DumJobNormalizer.uf(text(vaga, "State")));
        put(job, DumJobsSchema.REMOTE, DumJobNormalizer.remote(title, description));
        put(job, DumJobsSchema.SOURCE, SOURCE);
        put(job, DumJobsSchema.URL, DumJobNormalizer.siteUrl(siteUrl, title, id));
        put(job, DumJobsSchema.APPLY_URL, applyUrl(vaga));
        if (firstSeen != null) {
            String date = firstSeen.truncatedTo(ChronoUnit.SECONDS).toString();
            put(job, DumJobsSchema.PUBLISHED_AT, date);
            put(job, DumJobsSchema.MODIFICATION_DATE, date);
        }
        return job;
    }

    /**
     * The BNE job page, byte for byte as {@code BNE_Job_Integration::GetJobPostFromVagaBne} builds it:
     * {@code https://www.bne.com.br/vaga-de-emprego-na-area-{area}-em-{city}-{uf}/{title}/{idf}}, where
     * area, city and title go through {@link #pluginSlug}, the UF and {@code Idf_Vaga} are only lower
     * cased. Values are taken raw (untrimmed), as the plugin does; {@code null} without an
     * {@code Idf_Vaga}, the one part the page cannot do without.
     */
    static String applyUrl(JSONObject vaga) {
        String idf = raw(vaga, "Idf_Vaga");
        if (idf.isBlank()) {
            return null;
        }
        return APPLY_BASE + pluginSlug(raw(vaga, "Area")) + "-em-" + pluginSlug(raw(vaga, "City")) + "-"
                + lower(raw(vaga, "State")) + "/" + pluginSlug(raw(vaga, "Title")) + "/" + lower(idf);
    }

    /**
     * {@code mb_strtolower(preg_replace('/[ -]+/', '-', TirarAcentos($s)))}: the plugin's accent list
     * replaced, every run of spaces or hyphens one {@code -}, then lower case. Nothing else changes —
     * no trimming, and other characters ({@code ç}, {@code /}, {@code (}) stay as they are.
     */
    static String pluginSlug(String text) {
        String plain = text;
        for (String[] accent : PLUGIN_ACCENTS) {
            for (char c : accent[0].toCharArray()) {
                plain = plain.replace(String.valueOf(c), accent[1]);
            }
        }
        return lower(SPACES_OR_HYPHENS.matcher(plain).replaceAll("-"));
    }

    private static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    /** The value as sent, {@code ""} when missing or JSON null (PHP reads those as an empty string). */
    private static String raw(JSONObject object, String key) {
        if (object == null || !object.has(key) || object.isNull(key)) {
            return "";
        }
        return object.get(key).toString();
    }

    /** A trimmed string value, or {@code null} when missing, JSON null or blank. */
    private static String text(JSONObject object, String key) {
        String value = raw(object, key).trim();
        return value.isEmpty() ? null : value;
    }

    private static void put(Map<String, Object> job, String field, Object value) {
        if (value != null) {
            job.put(field, value);
        }
    }
}
