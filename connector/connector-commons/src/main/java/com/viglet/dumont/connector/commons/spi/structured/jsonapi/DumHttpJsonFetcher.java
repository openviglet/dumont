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

import com.viglet.dumont.connector.commons.http.DumHttpRetry;

/**
 * The JDK {@link HttpClient} behind a {@link DumJsonApiSource} (roadmap <strong>D92</strong>). Sends
 * {@code Accept: application/json} unless the manifest names its own, and treats any non-2xx status
 * as a failure so a source never parses an error page as an empty result. A {@code 429} or
 * {@code 503} is first waited out through the shared {@link DumHttpRetry} (roadmap D315), so a
 * provider's rate limit pauses the walk instead of ending it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumHttpJsonFetcher implements DumJsonFetcher {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);

    private final HttpClient client;
    private final DumHttpRetry retry;

    public DumHttpJsonFetcher() {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    public DumHttpJsonFetcher(HttpClient client) {
        this(client, new DumHttpRetry());
    }

    /** @param retry how a 429/503 is waited out; a test passes one with a recording sleeper */
    public DumHttpJsonFetcher(HttpClient client, DumHttpRetry retry) {
        this.client = client;
        this.retry = retry;
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
                    retry.send(client, builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP %d from %s".formatted(response.statusCode(),
                        DumHttpRetry.redact(request.url())));
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted fetching " + DumHttpRetry.redact(request.url()), e);
        }
    }
}
