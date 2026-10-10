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

package com.viglet.dumont.connector.plugin.jobs.source.http;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One partner API call (trabalharei <strong>TRA16</strong>): the method, the URI, extra headers, an
 * optional JSON body and the secrets the URI carries outside its query string. A partner that puts
 * its key in a path segment (Jooble: {@code POST /api/{apiKey}}) lists that key in {@code secrets},
 * and the fetcher masks it in every log line and error message.
 *
 * @param method  {@code GET} or {@code POST}
 * @param uri     the full request URI
 * @param headers extra request headers (never {@code null})
 * @param body    the JSON body of a POST, or {@code null}
 * @param secrets values to mask wherever the request is described (never {@code null})
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record DumJobRequest(String method, URI uri, Map<String, String> headers, String body,
        Set<String> secrets) {

    public static final String GET = "GET";
    public static final String POST = "POST";

    public DumJobRequest {
        if (uri == null) {
            throw new IllegalArgumentException("A job request needs a URI");
        }
        method = method == null ? GET : method.trim().toUpperCase(Locale.ROOT);
        if (!GET.equals(method) && !POST.equals(method)) {
            throw new IllegalArgumentException("Unsupported HTTP method for a job request: " + method);
        }
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        secrets = secrets == null ? Set.of()
                : secrets.stream().filter(s -> s != null && !s.isBlank())
                        .collect(Collectors.toUnmodifiableSet());
    }

    /** A GET with no secret outside the query string. */
    public static DumJobRequest get(URI uri, Map<String, String> headers) {
        return new DumJobRequest(GET, uri, headers, null, Set.of());
    }

    /** A JSON POST. */
    public static DumJobRequest post(URI uri, Map<String, String> headers, String body,
            Set<String> secrets) {
        return new DumJobRequest(POST, uri, headers, body, secrets);
    }
}
