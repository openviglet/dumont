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

package com.viglet.dumont.connector.plugin.jobs.source.occupation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import com.viglet.dumont.connector.commons.spi.structured.recipe.DumTaxonomy;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;

/**
 * The CBO 2002 occupation families as a versioned D19 {@link DumTaxonomy} (trabalharei
 * <strong>TRA22</strong>).
 *
 * <p>
 * The list ships as the classpath resource {@link #RESOURCE}: the Ministério do Trabalho's
 * {@code CBO2002 - Familia.csv} (the 4-digit family level, 617 families) with its own
 * {@code version}, {@code source} and {@code downloadedAt}. As with the IBGE list, D19 has no
 * resource loader and a taxonomy is a flat map (D99), so the table is the 4-digit family code &rarr;
 * the official title. The value the site indexes is the title's {@link #slug(String) slug}
 * ("Motoristas de veículos de cargas em geral" &rarr; {@code motoristas-de-veiculos-de-cargas-em-geral}).
 * {@link #load()} checks that every code has four digits, that no code or slug repeats and that the
 * declared {@code count} matches.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumCboFamilies {

    /** The taxonomy name in the recipe library. */
    public static final String TAXONOMY = "cbo-familias";

    /** The classpath resource the taxonomy is read from. */
    public static final String RESOURCE = "/jobs/cbo-familias.json";

    private static final Pattern CODE = Pattern.compile("\\d{4}");

    private DumCboFamilies() {
    }

    /** Lazily loaded once per class loader: the file never changes at runtime. */
    private static final class Holder {
        private static final DumTaxonomy TAXONOMY = read(resourceText());
    }

    /** The shipped taxonomy (family code &rarr; official title). */
    public static DumTaxonomy load() {
        return Holder.TAXONOMY;
    }

    /**
     * The slug of a family title: lower case, accents stripped, every run of non-alphanumerics one
     * {@code -}, none at either end.
     */
    public static String slug(String title) {
        return DumJobNormalizer.slug(title);
    }

    /** The slug of family {@code code} in {@code taxonomy}, or {@code null} when it holds no such code. */
    public static String slugOf(DumTaxonomy taxonomy, String code) {
        return taxonomy.canonical(code).map(DumCboFamilies::slug).orElse(null);
    }

    /** Parse a taxonomy file in the {@link #RESOURCE} format. */
    static DumTaxonomy read(String json) {
        JSONObject file = new JSONObject(json);
        DumTaxonomy.Builder taxonomy = DumTaxonomy.builder(file.getString("name"), file.getInt("version"));
        JSONArray rows = file.getJSONArray("familias");
        Map<String, String> codeBySlug = new HashMap<>();
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String code = row.getString("code");
            String title = row.getString("title").trim();
            if (!CODE.matcher(code).matches()) {
                throw new IllegalStateException("CBO family code '" + code + "' is not four digits");
            }
            if (!codes.add(code)) {
                throw new IllegalStateException("CBO family " + code + " is listed twice");
            }
            String other = codeBySlug.put(slug(title), code);
            if (other != null) {
                throw new IllegalStateException("CBO families " + other + " and " + code
                        + " share the slug " + slug(title));
            }
            taxonomy.map(code, title);
        }
        int expected = file.optInt("count", rows.length());
        if (expected != rows.length()) {
            throw new IllegalStateException("CBO family list declares " + expected + " rows but holds "
                    + rows.length());
        }
        return taxonomy.build();
    }

    private static String resourceText() {
        try (InputStream in = DumCboFamilies.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }
}
