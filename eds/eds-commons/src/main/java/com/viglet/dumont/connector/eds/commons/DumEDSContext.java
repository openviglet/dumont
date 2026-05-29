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

package com.viglet.dumont.connector.eds.commons;

import java.util.Collections;
import java.util.Map;

import org.jsoup.nodes.Document;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Per-page context handed to custom extensions
 * ({@link com.viglet.dumont.connector.eds.commons.ext.DumEDSExtInterface},
 * {@link com.viglet.dumont.connector.eds.commons.ext.DumEDSExtLocaleInterface}).
 *
 * <p>Contains everything an extension might need to compute a value:
 *
 * <ul>
 *   <li>{@code path} — site-relative path (e.g. {@code /pt-br/pf/conta-digital})</li>
 *   <li>{@code url}  — fully-qualified URL the document was fetched from</li>
 *   <li>{@code baseUrl} — site origin (the source's {@code baseUrl})</li>
 *   <li>{@code document} — parsed {@link Document}; may be null when the extension is
 *       invoked before fetch (e.g. locale resolvers that key off path only)</li>
 *   <li>{@code jsonMetadata} — row from the EDS sheet for this page (defensively
 *       wrapped as unmodifiable so extensions can read but not mutate state)</li>
 *   <li>{@code userAgent} — UA string used by the fetcher</li>
 * </ul>
 */
@Builder
@Getter
@Setter
public class DumEDSContext {
    private String path;
    private String url;
    private String baseUrl;
    private Document document;
    private Map<String, String> jsonMetadata;
    private String userAgent;

    public Map<String, String> getJsonMetadata() {
        return jsonMetadata == null ? Map.of() : Collections.unmodifiableMap(jsonMetadata);
    }
}
