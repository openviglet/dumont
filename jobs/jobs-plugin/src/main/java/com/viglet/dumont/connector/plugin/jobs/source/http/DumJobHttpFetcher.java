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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Default {@link DumJobFetcher}: a JDK {@link HttpClient} GET asking for JSON. Logs and error
 * messages name the host and path only ({@link #redacted(URI)}), never the query string, so an
 * {@code app_key} sent as a parameter does not end up in a log.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class DumJobHttpFetcher implements DumJobFetcher {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String get(URI uri, Map<String, String> headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMinutes(1))
                .header("Accept", "application/json")
                .GET();
        if (headers != null) {
            headers.forEach(request::header);
        }
        String where = redacted(uri);
        try {
            HttpResponse<String> response =
                    httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new DumJobFetchException("HTTP " + response.statusCode() + " from " + where);
            }
            log.debug("Fetched {} ({} bytes)", where, response.body().length());
            return response.body();
        } catch (IOException e) {
            throw new DumJobFetchException("GET " + where + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DumJobFetchException("GET " + where + " was interrupted", e);
        }
    }

    /** {@code uri} without its query string or user info — safe to log. */
    public static String redacted(URI uri) {
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
                + (uri.getRawPath() == null ? "" : uri.getRawPath());
    }
}
