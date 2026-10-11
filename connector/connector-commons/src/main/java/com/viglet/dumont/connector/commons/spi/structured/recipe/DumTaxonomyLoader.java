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

package com.viglet.dumont.connector.commons.spi.structured.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Reads a versioned {@link DumTaxonomy} from a JSON file (roadmap <strong>D99</strong>), so a
 * reference table ships as a resource instead of being typed into code:
 *
 * <pre>{@code
 * {
 *   "name": "ibge-municipios", "version": 3,
 *   "source": "https://servicodados.ibge.gov.br/api/v1/localidades/municipios",
 *   "downloadedAt": "2026-10-01",
 *   "key": "id", "canonical": "nome", "rows": "municipios", "count": 5571,
 *   "municipios": [ { "id": 2611606, "nome": "Recife", "uf": "PE" }, ... ]
 * }
 * }</pre>
 *
 * <p>
 * Each row maps its {@code key} column to its {@code canonical} column, and every other column
 * becomes an attribute of the row ({@link DumTaxonomy#attributes(String)}). {@code rows} is the
 * row array itself, or the name of the member holding it, as above. A declared {@code count} must match the
 * rows read, so a truncated download fails at load time instead of as missing matches. A flat
 * {@code "entries": {"raw": "canonical"}} object is read too, for a taxonomy with no columns.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumTaxonomyLoader {

    private DumTaxonomyLoader() {
    }

    /**
     * Reads a taxonomy from its JSON text.
     *
     * @throws IllegalArgumentException naming what is wrong with the file
     */
    public static DumTaxonomy fromJson(String json) {
        JSONObject file;
        try {
            file = new JSONObject(json);
        } catch (JSONException e) {
            throw new IllegalArgumentException("A taxonomy file must be a JSON object: " + e.getMessage(), e);
        }
        String name = file.optString("name", null);
        DumTaxonomy.Builder taxonomy = DumTaxonomy.builder(name, file.optInt("version", 0))
                .source(file.optString("source", null))
                .downloadedAt(file.optString("downloadedAt", null));
        JSONObject entries = file.optJSONObject("entries");
        if (entries != null) {
            entries.keySet().forEach(raw -> taxonomy.map(raw, entries.get(raw).toString()));
            return taxonomy.build();
        }
        String key = required(file, "key", name);
        String canonical = required(file, "canonical", name);
        // "rows" is either the row array itself or the name of the member holding it.
        JSONArray inline = file.optJSONArray("rows");
        String rowsKey = inline != null ? "rows" : file.optString("rows", "rows");
        JSONArray rows = inline != null ? inline : file.optJSONArray(rowsKey);
        if (rows == null) {
            throw new IllegalArgumentException(
                    "Taxonomy '%s' has no row array under '%s'".formatted(name, rowsKey));
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !row.has(key) || !row.has(canonical)) {
                throw new IllegalArgumentException("Taxonomy '%s' row %d lacks its '%s' or '%s' column"
                        .formatted(name, i, key, canonical));
            }
            Map<String, String> attributes = new LinkedHashMap<>();
            for (String column : row.keySet()) {
                if (!column.equals(key) && !column.equals(canonical) && !row.isNull(column)) {
                    attributes.put(column, row.get(column).toString());
                }
            }
            taxonomy.row(row.get(key).toString(), row.get(canonical).toString(), attributes);
        }
        if (file.has("count") && file.getInt("count") != rows.length()) {
            throw new IllegalArgumentException("Taxonomy '%s' declares %d rows but holds %d"
                    .formatted(name, file.getInt("count"), rows.length()));
        }
        return taxonomy.build();
    }

    /**
     * Reads a taxonomy from a classpath resource.
     *
     * @param loader   the class loader to read through, usually the calling plugin's
     * @param resource the resource path, e.g. {@code jobs/ibge-municipios.json}
     * @throws IllegalArgumentException when the resource is missing or its content is invalid
     */
    public static DumTaxonomy fromResource(ClassLoader loader, String resource) {
        String path = resource.startsWith("/") ? resource.substring(1) : resource;
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("No taxonomy resource at " + path);
            }
            return fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read taxonomy resource " + path, e);
        }
    }

    private static String required(JSONObject file, String field, String name) {
        String value = file.optString(field, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Taxonomy '%s' must name its '%s' column".formatted(name, field));
        }
        return value;
    }
}
