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

package com.viglet.dumont.connector.eds.commons.domain;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * The fully-resolved view of a single EDS page after fetch + extract + merge:
 *
 * <ul>
 *   <li>{@code path} — site-relative path (same as in {@link DumEDSDiscoveredPage})</li>
 *   <li>{@code metadata} — merged JSON-index ∪ HTML metadata (HTML wins on conflict)</li>
 *   <li>{@code body} — plaintext body extracted from the configured HTML selector,
 *       with {@code removeSelectors} stripped and whitespace normalised</li>
 *   <li>{@code lastModified} — HTTP {@code Last-Modified} header if present, otherwise
 *       the {@code lastModified} column from the index. Null when neither is known.</li>
 *   <li>{@code checksum} — content fingerprint used by the indexing pipeline to
 *       detect no-op updates. Filled in by Phase 4.</li>
 *   <li>{@code locale} — per-page locale, populated when the source declares a
 *       {@code localeClass} extension. Null means "fall back to the source's
 *       explicit locale, then Locale.US."</li>
 * </ul>
 */
public record DumEDSFetchedPage(
        String path,
        Map<String, String> metadata,
        String body,
        String lastModified,
        String checksum,
        Locale locale) {

    public DumEDSFetchedPage {
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(metadata);
    }

    public static DumEDSFetchedPage of(String path, Map<String, String> metadata, String body) {
        return new DumEDSFetchedPage(path, metadata, body, null, null, null);
    }

    public DumEDSFetchedPage withChecksum(String checksum) {
        return new DumEDSFetchedPage(path, metadata, body, lastModified, checksum, locale);
    }

    public DumEDSFetchedPage withLastModified(String lastModified) {
        return new DumEDSFetchedPage(path, metadata, body, lastModified, checksum, locale);
    }

    public DumEDSFetchedPage withLocale(Locale locale) {
        return new DumEDSFetchedPage(path, metadata, body, lastModified, checksum, locale);
    }
}
