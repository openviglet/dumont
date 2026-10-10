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

package com.viglet.dumont.connector.plugin.jobs.source.location;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.viglet.dumont.connector.commons.spi.structured.recipe.DumTaxonomy;

/**
 * The IBGE municipality list as a versioned D19 {@link DumTaxonomy} (trabalharei
 * <strong>TRA20</strong>).
 *
 * <p>
 * The list ships as the classpath resource {@link #RESOURCE}: a trimmed copy of
 * {@code servicodados.ibge.gov.br/api/v1/localidades/municipios} (code, name and UF only) carrying its
 * own {@code version} and {@code downloadedAt}. D19 has no resource loader and a {@link DumTaxonomy} is
 * a flat {@code raw -> canonical} map, so the table is the 7-digit IBGE code &rarr; the official name
 * with accents. The UF is not lost: the first two digits of an IBGE code are the state code, and
 * {@link #ufOf(String)} reads it back. {@link #load()} checks every row's {@code uf} against that
 * prefix, so the encoding cannot drift from the file.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumIbgeMunicipalities {

    /** The taxonomy name in the recipe library. */
    public static final String TAXONOMY = "ibge-municipios";

    /** The classpath resource the taxonomy is read from. */
    public static final String RESOURCE = "/jobs/ibge-municipios.json";

    /** IBGE state code (first two digits of a municipality code) to UF. */
    private static final Map<String, String> UF_BY_PREFIX = Map.ofEntries(
            Map.entry("11", "RO"), Map.entry("12", "AC"), Map.entry("13", "AM"),
            Map.entry("14", "RR"), Map.entry("15", "PA"), Map.entry("16", "AP"),
            Map.entry("17", "TO"), Map.entry("21", "MA"), Map.entry("22", "PI"),
            Map.entry("23", "CE"), Map.entry("24", "RN"), Map.entry("25", "PB"),
            Map.entry("26", "PE"), Map.entry("27", "AL"), Map.entry("28", "SE"),
            Map.entry("29", "BA"), Map.entry("31", "MG"), Map.entry("32", "ES"),
            Map.entry("33", "RJ"), Map.entry("35", "SP"), Map.entry("41", "PR"),
            Map.entry("42", "SC"), Map.entry("43", "RS"), Map.entry("50", "MS"),
            Map.entry("51", "MT"), Map.entry("52", "GO"), Map.entry("53", "DF"));

    private DumIbgeMunicipalities() {
    }

    /** Lazily loaded once per class loader: the file is ~300 KB and never changes at runtime. */
    private static final class Holder {
        private static final DumTaxonomy TAXONOMY = read();
    }

    /** The shipped taxonomy (IBGE code &rarr; municipality name). */
    public static DumTaxonomy load() {
        return Holder.TAXONOMY;
    }

    /** The UF of an IBGE municipality code, or {@code null} when the prefix names no state. */
    public static String ufOf(String code) {
        if (code == null || code.length() < 2) {
            return null;
        }
        return UF_BY_PREFIX.get(code.substring(0, 2));
    }

    private static DumTaxonomy read() {
        JSONObject file = new JSONObject(resourceText());
        DumTaxonomy.Builder taxonomy = DumTaxonomy.builder(file.getString("name"), file.getInt("version"));
        JSONArray rows = file.getJSONArray("municipios");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String code = String.valueOf(row.getLong("id"));
            String uf = row.getString("uf");
            if (!uf.equals(ufOf(code))) {
                throw new IllegalStateException("IBGE municipality " + code + " is listed under " + uf
                        + " but its code prefix names " + ufOf(code));
            }
            taxonomy.map(code, row.getString("nome"));
        }
        int expected = file.optInt("count", rows.length());
        if (expected != rows.length()) {
            throw new IllegalStateException("IBGE municipality list declares " + expected
                    + " rows but holds " + rows.length());
        }
        return taxonomy.build();
    }

    private static String resourceText() {
        try (InputStream in = DumIbgeMunicipalities.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }
}
