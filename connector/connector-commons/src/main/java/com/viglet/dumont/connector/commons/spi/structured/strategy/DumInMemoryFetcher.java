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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

/**
 * A {@link DumHtmlFetcher} that serves canned markup from a {@code url -> content} map, parsing it
 * with the right jsoup parser and keeping the URL as the base URI so {@code abs:href} resolves the
 * same way it would for a live page.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
final class DumInMemoryFetcher implements DumHtmlFetcher {

    private final Map<String, String> pagesByUrl;

    DumInMemoryFetcher(Map<String, String> pagesByUrl) {
        this.pagesByUrl = Map.copyOf(pagesByUrl);
    }

    @Override
    public Document html(String url) throws IOException {
        return Jsoup.parse(content(url), url);
    }

    @Override
    public Document xml(String url) throws IOException {
        return Jsoup.parse(content(url), url, Parser.xmlParser());
    }

    private String content(String url) throws IOException {
        String content = pagesByUrl.get(url);
        if (content == null) {
            throw new FileNotFoundException("No in-memory page registered for URL: " + url);
        }
        return content;
    }
}
