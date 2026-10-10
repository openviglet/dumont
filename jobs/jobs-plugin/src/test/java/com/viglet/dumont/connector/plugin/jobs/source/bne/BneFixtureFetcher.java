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

package com.viglet.dumont.connector.plugin.jobs.source.bne;

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
 * Answers BNE list requests from {@code /fixtures/bne/page-<n>.json}, whatever the UF; a page with no
 * fixture answers {@code empty.json}. A test can replace a page's body. Never reaches the network.
 */
final class BneFixtureFetcher implements DumJobFetcher {

    final List<URI> requests = new ArrayList<>();
    final List<Map<String, String>> headers = new ArrayList<>();
    /** Requests (1-based) that fail with an HTTP 500. */
    final List<Integer> failing = new ArrayList<>();
    /** Bodies that replace a fixture, by page number. */
    final Map<String, String> overrides = new HashMap<>();

    @Override
    public String get(URI uri, Map<String, String> requestHeaders) {
        requests.add(uri);
        headers.add(requestHeaders);
        if (failing.contains(requests.size())) {
            throw new DumJobFetchException("HTTP 500 from GET " + uri.getPath());
        }
        String page = query(uri).get("Page");
        String body = overrides.containsKey(page) ? overrides.get(page) : read("page-" + page);
        return body != null ? body : read("empty");
    }

    /** The decoded query parameters of {@code uri}. */
    static Map<String, String> query(URI uri) {
        Map<String, String> values = new LinkedHashMap<>();
        String raw = uri.getRawQuery();
        if (raw != null) {
            for (String pair : raw.split("&")) {
                int eq = pair.indexOf('=');
                values.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    /** Each request as {@code "<UF or all>:<page>"}. */
    List<String> pages() {
        List<String> pages = new ArrayList<>();
        for (URI uri : requests) {
            Map<String, String> values = query(uri);
            pages.add(values.getOrDefault("State", "all") + ":" + values.get("Page"));
        }
        return pages;
    }

    private static final Map<String, String> CACHE = new HashMap<>();

    static String read(String name) {
        return CACHE.computeIfAbsent(name, n -> {
            try (InputStream in = BneFixtureFetcher.class
                    .getResourceAsStream("/fixtures/bne/" + n + ".json")) {
                return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
