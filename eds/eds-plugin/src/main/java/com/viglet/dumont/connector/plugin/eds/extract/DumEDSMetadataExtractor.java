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

package com.viglet.dumont.connector.plugin.eds.extract;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Extracts metadata from an HTML document into the same flat string-string map
 * shape that the EDS sheet produces. Sources, in priority order (later overrides
 * earlier so the final map is the most-specific):
 *
 * <ol>
 *   <li>{@code <title>} → {@code title}</li>
 *   <li>{@code <meta name="description">} → {@code description}</li>
 *   <li>{@code <meta name="modified-time">} → {@code lastModified}</li>
 *   <li>{@code <meta name="published-time">} → {@code publishedTime}</li>
 *   <li>{@code <meta property="og:title">} → {@code title}</li>
 *   <li>{@code <meta property="og:description">} → {@code description}</li>
 *   <li>{@code <meta property="og:image">} → {@code image}</li>
 * </ol>
 *
 * <p>EDS pages typically populate {@code og:*} tags from the same sheet metadata
 * the {@code /query-index.json} exposes, so this is the gap-filling story for
 * pages whose row in the index was sparse.
 */
@Component
public class DumEDSMetadataExtractor {

    public static final String FIELD_TITLE = "title";
    public static final String FIELD_DESCRIPTION = "description";
    public static final String FIELD_IMAGE = "image";
    public static final String FIELD_LAST_MODIFIED = "lastModified";
    public static final String FIELD_PUBLISHED_TIME = "publishedTime";

    public Map<String, String> extract(Document doc) {
        Map<String, String> out = new LinkedHashMap<>();
        if (doc == null) {
            return out;
        }

        String title = textOrNull(doc.selectFirst("title"));
        putIfPresent(out, FIELD_TITLE, title);

        putMetaName(out, doc, "description", FIELD_DESCRIPTION);
        putMetaName(out, doc, "modified-time", FIELD_LAST_MODIFIED);
        putMetaName(out, doc, "published-time", FIELD_PUBLISHED_TIME);

        putMetaProperty(out, doc, "og:title", FIELD_TITLE);
        putMetaProperty(out, doc, "og:description", FIELD_DESCRIPTION);
        putMetaProperty(out, doc, "og:image", FIELD_IMAGE);

        return out;
    }

    private static void putMetaName(Map<String, String> out, Document doc, String metaName,
            String targetKey) {
        Element el = doc.selectFirst("meta[name=" + metaName + "]");
        if (el != null) {
            putIfPresent(out, targetKey, el.attr("content"));
        }
    }

    private static void putMetaProperty(Map<String, String> out, Document doc, String property,
            String targetKey) {
        Element el = doc.selectFirst("meta[property=" + property + "]");
        if (el != null) {
            putIfPresent(out, targetKey, el.attr("content"));
        }
    }

    private static String textOrNull(Element el) {
        return el == null ? null : el.text();
    }

    private static void putIfPresent(Map<String, String> out, String key, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.isEmpty()) {
            out.put(key, trimmed);
        }
    }
}
