/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.plugin.eds.job;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.eds.commons.domain.DumEDSFetchedPage;
import com.viglet.dumont.connector.plugin.eds.enrichment.DumEDSFacetDeriver;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSFieldMapping;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;

/**
 * Builds a {@link TurSNJobItem} for a single fetched EDS page. The output is
 * deterministic for a given (source, page) pair — same merged metadata, same
 * body, same facets produces the same checksum, which lets the central
 * {@code UnchangedStrategy} skip no-op updates on subsequent crawls (spec test
 * case 6).
 *
 * <p>Attribute keys are passed through the source's {@link DumEDSFieldMapping}
 * (identity mapping when none configured), so an operator can rename
 * {@code title} → {@code page_title} without touching the connector.
 */
@Component
public class DumEDSJobItemBuilder {

    public static final String ATTR_ID = "id";
    public static final String ATTR_URL = "url";
    public static final String ATTR_PATH = "path";
    public static final String ATTR_BODY = "body";
    public static final String ATTR_LAST_MODIFIED = "lastModified";
    public static final String SOURCE_APPS = "source_apps";
    public static final String PROVIDER_NAME = "EDS";

    /**
     * ASCII SOH (Start of Heading) — a non-printable single-byte UTF-8 char
     * used as field separator inside the checksum canonical form so no real
     * value can collide with the separator.
     */
    static final char CHECKSUM_SEPARATOR = '\u0001';

    private final DumEDSFacetDeriver facetDeriver;

    public DumEDSJobItemBuilder(DumEDSFacetDeriver facetDeriver) {
        this.facetDeriver = facetDeriver;
    }

    public TurSNJobItem build(DumEDSSource source, DumEDSFetchedPage page) {
        Map<String, Object> attributes = buildAttributes(source, page);
        String checksum = computeChecksum(attributes);
        return new TurSNJobItem(
                TurSNJobAction.CREATE,
                new ArrayList<>(source.getTurSNSites()),
                effectiveLocale(source, page),
                attributes,
                null,
                checksum);
    }

    /**
     * Builds a DELETE TurSNJobItem for a path that has disappeared from
     * discovery. The downstream Turing engine uses {@code id} (URL) to locate
     * the document; {@code source_apps} is included so the deletion can be
     * audited back to this plugin.
     */
    public TurSNJobItem buildDelete(DumEDSSource source, String path) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        String url = buildUrl(source.getBaseUrl(), path);
        attributes.put(ATTR_ID, url);
        attributes.put(SOURCE_APPS, PROVIDER_NAME);
        return new TurSNJobItem(
                TurSNJobAction.DELETE,
                new ArrayList<>(source.getTurSNSites()),
                effectiveLocale(source, null),
                attributes,
                null,
                null);
    }

    /**
     * Build attributes deterministically. Ordering matters for checksum stability:
     * {@link #computeChecksum} sorts by key, but insertion order is preserved so
     * downstream consumers that iterate get something sensible (id/url/path first).
     */
    Map<String, Object> buildAttributes(DumEDSSource source, DumEDSFetchedPage page) {
        Map<String, String> fieldMap = fieldMappingsOf(source);
        Map<String, Object> attributes = new LinkedHashMap<>();
        String url = buildUrl(source.getBaseUrl(), page.path());
        putMapped(attributes, fieldMap, ATTR_ID, url);
        putMapped(attributes, fieldMap, ATTR_URL, url);
        putMapped(attributes, fieldMap, ATTR_PATH, page.path());

        for (Map.Entry<String, String> e : page.metadata().entrySet()) {
            if (ATTR_PATH.equals(e.getKey())) {
                continue;
            }
            putMapped(attributes, fieldMap, e.getKey(), e.getValue());
        }

        if (page.body() != null && !page.body().isBlank()) {
            putMapped(attributes, fieldMap, ATTR_BODY, page.body());
        }

        if (page.lastModified() != null && !page.lastModified().isBlank()) {
            putMapped(attributes, fieldMap, ATTR_LAST_MODIFIED, page.lastModified());
        }

        Map<String, String> facets = facetDeriver.derive(page.path(), source);
        for (Map.Entry<String, String> e : facets.entrySet()) {
            putMapped(attributes, fieldMap, e.getKey(), e.getValue());
        }

        putMapped(attributes, fieldMap, SOURCE_APPS, PROVIDER_NAME);

        return attributes;
    }

    static String computeChecksum(Map<String, Object> attributes) {
        StringBuilder canonical = new StringBuilder();
        List<Map.Entry<String, Object>> sorted = new ArrayList<>(attributes.entrySet());
        sorted.sort(Comparator.comparing(Map.Entry::getKey));
        for (Map.Entry<String, Object> e : sorted) {
            canonical.append(e.getKey()).append('=')
                    .append(e.getValue() == null ? "" : e.getValue().toString())
                    .append(CHECKSUM_SEPARATOR);
        }
        CRC32 crc = new CRC32();
        crc.update(canonical.toString().getBytes(StandardCharsets.UTF_8));
        return Long.toUnsignedString(crc.getValue());
    }

    /**
     * Resolution order: explicit {@code source.locale} → per-page locale from
     * the {@code localeClass} extension (carried on {@code page}) → {@code Locale.US}.
     */
    static Locale effectiveLocale(DumEDSSource source, DumEDSFetchedPage page) {
        if (source.getLocale() != null) {
            return source.getLocale();
        }
        if (page != null && page.locale() != null) {
            return page.locale();
        }
        return Locale.US;
    }

    static String buildUrl(String baseUrl, String path) {
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String p = path.startsWith("/") ? path : "/" + path;
        return b + p;
    }

    static Map<String, String> fieldMappingsOf(DumEDSSource source) {
        Map<String, String> map = new LinkedHashMap<>();
        if (source.getFieldMappings() == null) {
            return map;
        }
        for (DumEDSFieldMapping fm : source.getFieldMappings()) {
            if (fm.getSourceField() != null && !fm.getSourceField().isBlank()
                    && fm.getTargetField() != null && !fm.getTargetField().isBlank()) {
                map.put(fm.getSourceField(), fm.getTargetField());
            }
        }
        return map;
    }

    static void putMapped(Map<String, Object> attributes, Map<String, String> fieldMap,
            String sourceField, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String s && s.isBlank()) {
            return;
        }
        String target = fieldMap.getOrDefault(sourceField, sourceField);
        attributes.put(target, value);
    }
}
