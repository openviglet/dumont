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

package com.viglet.dumont.connector.plugin.jobs.source.adzuna;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobFetchException;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobFetcher;

/**
 * A {@link DumJobFetcher} that answers from the recorded Adzuna fixtures under
 * {@code fixtures/adzuna/} and logs every request. The newest-first query serves {@code page-N.json};
 * a category query serves {@code category-<tag>-page-N.json}; anything else is {@code empty.json}.
 */
final class FixtureFetcher implements DumJobFetcher {

    final List<URI> requests = new ArrayList<>();
    /** Requests (1-based) that fail with an HTTP 500. */
    final List<Integer> failing = new ArrayList<>();

    @Override
    public String get(URI uri, Map<String, String> headers) {
        requests.add(uri);
        if (failing.contains(requests.size())) {
            throw new DumJobFetchException("HTTP 500 from " + uri.getHost() + uri.getPath());
        }
        String path = uri.getPath();
        String page = path.substring(path.lastIndexOf('/') + 1);
        String category = query(uri).get("category");
        String name = category == null ? "page-" + page : "category-" + category + "-page-" + page;
        String body = read(name);
        return body != null ? body : read("empty");
    }

    /** The decoded query parameters of {@code uri}. */
    static Map<String, String> query(URI uri) {
        Map<String, String> params = new LinkedHashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null) {
            return params;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            params.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return params;
    }

    /** The page number and category of each request, as {@code "<category or newest>:<page>"}. */
    List<String> pages() {
        List<String> pages = new ArrayList<>();
        for (URI uri : requests) {
            String category = query(uri).getOrDefault("category", "newest");
            pages.add(category + ":" + uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1));
        }
        return pages;
    }

    private static final Map<String, String> CACHE = new HashMap<>();

    static String read(String name) {
        return CACHE.computeIfAbsent(name, n -> {
            try (InputStream in = FixtureFetcher.class
                    .getResourceAsStream("/fixtures/adzuna/" + n + ".json")) {
                return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
