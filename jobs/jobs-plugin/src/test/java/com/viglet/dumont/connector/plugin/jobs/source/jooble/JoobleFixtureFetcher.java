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

package com.viglet.dumont.connector.plugin.jobs.source.jooble;

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

import org.json.JSONObject;

import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobFetchException;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobFetcher;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobRequest;

/**
 * Answers Jooble requests from {@code /fixtures/jooble/page-<n>.json}, whatever the keywords; a page
 * with no fixture answers {@code empty.json}. Never reaches the network.
 */
final class JoobleFixtureFetcher implements DumJobFetcher {

    final List<DumJobRequest> requests = new ArrayList<>();
    /** Requests (1-based) that fail with an HTTP 500. */
    final List<Integer> failing = new ArrayList<>();

    @Override
    public String get(URI uri, Map<String, String> headers) {
        throw new UnsupportedOperationException("The Jooble adapter sends DumJobRequests");
    }

    @Override
    public String send(DumJobRequest request) {
        requests.add(request);
        if (failing.contains(requests.size())) {
            throw new DumJobFetchException("HTTP 500 from " + request.method() + " jooble");
        }
        String body = read("page-" + values(request).get("page"));
        return body != null ? body : read("empty");
    }

    /** The search values of {@code request}: its JSON body for a POST, its query for a GET. */
    static Map<String, String> values(DumJobRequest request) {
        Map<String, String> values = new LinkedHashMap<>();
        if (request.body() != null) {
            JSONObject body = new JSONObject(request.body());
            body.keySet().forEach(k -> values.put(k, body.get(k).toString()));
            return values;
        }
        String raw = request.uri().getRawQuery();
        if (raw != null) {
            for (String pair : raw.split("&")) {
                int eq = pair.indexOf('=');
                values.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    /** Each request as {@code "<keywords>:<page>"}. */
    List<String> pages() {
        List<String> pages = new ArrayList<>();
        for (DumJobRequest request : requests) {
            Map<String, String> values = values(request);
            pages.add(values.get("keywords") + ":" + values.get("page"));
        }
        return pages;
    }

    private static final Map<String, String> CACHE = new HashMap<>();

    static String read(String name) {
        return CACHE.computeIfAbsent(name, n -> {
            try (InputStream in = JoobleFixtureFetcher.class
                    .getResourceAsStream("/fixtures/jooble/" + n + ".json")) {
                return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
