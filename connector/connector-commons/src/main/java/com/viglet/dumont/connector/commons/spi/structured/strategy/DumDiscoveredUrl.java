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

package com.viglet.dumont.connector.commons.spi.structured.strategy;

/**
 * A detail-page URL surfaced by a {@link DumDiscoveryStrategy}, carrying the <em>level</em> it was
 * classified into (e.g. {@code "undergraduate"} vs {@code "graduate"}). The level is the strategy's
 * one piece of derived knowledge — a sitemap classifies it by URL pattern, a listing inherits it
 * from the listing source — and a source typically copies it into a declared field at extract time.
 *
 * @param url   the absolute detail-page URL (also the content id passed to {@code extract})
 * @param level the classification level, or {@code null} when the strategy does not classify
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record DumDiscoveredUrl(String url, String level) {

    public DumDiscoveredUrl {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Discovered URL is required");
        }
    }

    public static DumDiscoveredUrl of(String url) {
        return new DumDiscoveredUrl(url, null);
    }
}
