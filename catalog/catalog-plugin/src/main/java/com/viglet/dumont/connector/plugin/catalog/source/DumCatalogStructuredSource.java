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

package com.viglet.dumont.connector.plugin.catalog.source;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.plugin.catalog.DumCatalogPlugin;
import com.viglet.dumont.connector.plugin.catalog.DumCatalogSchema;
import com.viglet.dumont.connector.plugin.catalog.fetch.DumCatalogFetcher;
import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogSource;

import lombok.extern.slf4j.Slf4j;

/**
 * A {@link DumStructuredSource} backed by one institution's already-normalized catalog JSON
 * (roadmap <strong>D10</strong>). {@link #discover()} fetches the payload once and indexes it by the
 * record id the engine assigned; {@link #extract(String)} maps each record's camelCase fields onto
 * the {@link DumCatalogSchema} — the schema-bound builder enforces grounding, so an absent field is
 * omitted, never published as {@code ""}. The bridge re-publishes; it never re-extracts.
 *
 * <p>
 * The fetched payload is memoized on the instance: the indexing loop resolves a source once, then
 * calls {@code discover()} followed by many {@code extract()} calls on the same instance, so a single
 * fetch serves the whole run.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public class DumCatalogStructuredSource implements DumStructuredSource {

    private static final String ID_KEY = "id";
    private static final String COURSES_KEY = "courses";

    private final DumCatalogSource source;
    private final DumStructuredSchema schema;
    private final DumCatalogFetcher fetcher;

    private Map<String, JSONObject> records;

    public DumCatalogStructuredSource(DumCatalogSource source, DumStructuredSchema schema,
            DumCatalogFetcher fetcher) {
        this.source = source;
        this.schema = schema;
        this.fetcher = fetcher;
    }

    @Override
    public String getSourceName() {
        return source.getName();
    }

    @Override
    public String getProviderName() {
        return DumCatalogPlugin.PROVIDER_NAME;
    }

    @Override
    public Collection<String> getSiteNames() {
        return source.getTurSNSites();
    }

    @Override
    public DumStructuredSchema schema() {
        return schema;
    }

    @Override
    public Locale getLocale() {
        String locale = source.getLocale();
        if (locale == null || locale.isBlank()) {
            return Locale.of("pt", "BR");
        }
        String[] parts = locale.split("[_-]", 2);
        return parts.length == 2 ? Locale.of(parts[0], parts[1]) : Locale.of(parts[0]);
    }

    @Override
    public List<String> discover() {
        return new ArrayList<>(load().keySet());
    }

    @Override
    public Optional<DumStructuredRecord> extract(String contentId) {
        JSONObject json = load().get(contentId);
        if (json == null) {
            return Optional.empty();
        }
        DumStructuredRecord.Builder record = DumStructuredRecord.builder(contentId, schema);
        for (String field : DumCatalogSchema.FIELDS) {
            Object value = value(json, field);
            if (value != null) {
                record.set(field, value);
            }
        }
        return Optional.of(record.build());
    }

    /** Fetch + parse the catalog once, indexing records by their engine-assigned id. */
    private synchronized Map<String, JSONObject> load() {
        if (records != null) {
            return records;
        }
        Map<String, JSONObject> indexed = new LinkedHashMap<>();
        JSONArray courses = parse(fetcher.fetch(source));
        for (int i = 0; i < courses.length(); i++) {
            JSONObject course = courses.optJSONObject(i);
            if (course == null) {
                continue;
            }
            String id = course.optString(ID_KEY, null);
            if (id == null || id.isBlank()) {
                log.warn("Catalog source '{}' has a record without an id — skipped", source.getName());
                continue;
            }
            indexed.put(id, course);
        }
        log.info("Catalog source '{}' discovered {} record(s)", source.getName(), indexed.size());
        records = indexed;
        return records;
    }

    /** Accept either a bare JSON array of records, or the merged {@code {courses:[...]}} envelope. */
    private static JSONArray parse(String payload) {
        String trimmed = payload == null ? "" : payload.trim();
        if (trimmed.startsWith("[")) {
            return new JSONArray(trimmed);
        }
        JSONObject envelope = new JSONObject(trimmed);
        return envelope.optJSONArray(COURSES_KEY) != null ? envelope.getJSONArray(COURSES_KEY)
                : new JSONArray();
    }

    /**
     * Read one field from the record. A missing or JSON-null key returns {@code null} (the field
     * stays absent under grounding); an array becomes a {@code List} for multi-valued fields; scalars
     * pass through as their JSON type (String/Boolean/Number) for the schema to validate.
     */
    private static Object value(JSONObject json, String field) {
        if (!json.has(field) || json.isNull(field)) {
            return null;
        }
        Object raw = json.get(field);
        if (raw instanceof JSONArray array) {
            List<Object> values = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                if (!array.isNull(i)) {
                    values.add(array.get(i));
                }
            }
            return values;
        }
        return raw;
    }
}
