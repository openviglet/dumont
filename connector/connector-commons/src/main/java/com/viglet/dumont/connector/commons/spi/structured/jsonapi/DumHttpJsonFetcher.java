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

package com.viglet.dumont.connector.commons.spi.structured.jsonapi;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The JDK {@link HttpClient} behind a {@link DumJsonApiSource} (roadmap <strong>D92</strong>). Sends
 * {@code Accept: application/json} unless the manifest names its own, and treats any non-2xx status
 * as a failure so a source never parses an error page as an empty result.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumHttpJsonFetcher implements DumJsonFetcher {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);

    private final HttpClient client;

    public DumHttpJsonFetcher() {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    public DumHttpJsonFetcher(HttpClient client) {
        this.client = client;
    }

    @Override
    public String fetch(DumJsonApiRequest request) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(request.url()))
                .timeout(REQUEST_TIMEOUT);
        if (request.headers().keySet().stream().noneMatch("accept"::equalsIgnoreCase)) {
            builder.header("Accept", "application/json");
        }
        request.headers().forEach(builder::header);
        if ("POST".equals(request.method())) {
            if (request.headers().keySet().stream().noneMatch("content-type"::equalsIgnoreCase)) {
                builder.header("Content-Type", "application/json");
            }
            builder.POST(HttpRequest.BodyPublishers.ofString(
                    request.body() == null ? "" : request.body()));
        } else {
            builder.GET();
        }
        try {
            HttpResponse<String> response =
                    client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP %d from %s".formatted(response.statusCode(),
                        redact(request.url())));
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted fetching " + redact(request.url()), e);
        }
    }

    /** The URL without its query, which is where an API key usually travels. */
    static String redact(String url) {
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query) + "?…";
    }
}
