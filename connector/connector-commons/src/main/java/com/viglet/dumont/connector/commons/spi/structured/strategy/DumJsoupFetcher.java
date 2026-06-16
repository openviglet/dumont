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

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

/**
 * The production {@link DumHtmlFetcher}: a jsoup HTTP client with a browser user agent, referrer and
 * timeout — the same posture the web crawler uses. XML resources (sitemaps) are fetched with jsoup's
 * XML parser so {@code <loc>} entries survive unescaped.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumJsoupFetcher implements DumHtmlFetcher {

    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final String DEFAULT_REFERRER = "https://www.google.com";
    private static final int DEFAULT_TIMEOUT = 15000;

    private final String userAgent;
    private final String referrer;
    private final int timeout;

    public DumJsoupFetcher() {
        this(DEFAULT_USER_AGENT, DEFAULT_REFERRER, DEFAULT_TIMEOUT);
    }

    public DumJsoupFetcher(String userAgent, String referrer, int timeout) {
        this.userAgent = userAgent;
        this.referrer = referrer;
        this.timeout = timeout;
    }

    @Override
    public Document html(String url) throws IOException {
        return connect(url).get();
    }

    @Override
    public Document xml(String url) throws IOException {
        return connect(url).parser(Parser.xmlParser()).get();
    }

    private Connection connect(String url) {
        return Jsoup.connect(url)
                .userAgent(userAgent)
                .referrer(referrer)
                .timeout(timeout)
                .maxBodySize(0)
                .ignoreContentType(true);
    }
}
