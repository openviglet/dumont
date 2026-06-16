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
 * One listing page for {@link DumHtmlListingStrategy}: anchors matched by {@link #selector()} on the
 * page at {@link #url()} become detail-page URLs, each carrying {@link #level()} as its level. This
 * is the {@code {url, level, selector}} triple cursarei's HTML-listing crawler configures per source.
 *
 * @param url      the listing page to fetch
 * @param selector the CSS selector for the detail anchors (e.g. {@code "a[href*='/curso/']"})
 * @param level    the level assigned to every detail URL found on this page (may be {@code null})
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record DumListingSource(String url, String selector, String level) {

    public DumListingSource {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Listing source URL is required");
        }
        if (selector == null || selector.isBlank()) {
            throw new IllegalArgumentException("Listing source selector is required");
        }
    }

    public static DumListingSource of(String url, String selector) {
        return new DumListingSource(url, selector, null);
    }

    public static DumListingSource of(String url, String selector, String level) {
        return new DumListingSource(url, selector, level);
    }
}
