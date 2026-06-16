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

import java.io.IOException;

import org.jsoup.nodes.Document;

/**
 * The fetch seam used by the declarative extraction strategies (roadmap <strong>D02</strong>).
 *
 * <p>
 * A strategy never opens a connection itself — it asks the fetcher for a parsed document. This keeps
 * the strategies pure and unit-testable: production wires {@link DumJsoupFetcher} (a real HTTP
 * client), while tests pass an in-memory fetcher backed by canned markup (see
 * {@link #ofPages(java.util.Map)}). HTML and XML are parsed with the appropriate jsoup parser so
 * sitemaps keep their {@code <loc>} casing and self-closing tags.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface DumHtmlFetcher {

    /** Fetch and parse a URL as an HTML document. */
    Document html(String url) throws IOException;

    /** Fetch and parse a URL as an XML document (sitemaps, feeds). */
    Document xml(String url) throws IOException;

    /**
     * An in-memory fetcher that serves canned markup keyed by URL — for tests and for sources whose
     * content is already in hand. The map value is parsed as HTML by {@link #html(String)} and as
     * XML by {@link #xml(String)}, with the URL kept as the base URI so {@code abs:href} resolves.
     */
    static DumHtmlFetcher ofPages(java.util.Map<String, String> pagesByUrl) {
        return new DumInMemoryFetcher(pagesByUrl);
    }
}
