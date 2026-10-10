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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Default {@link DumJobFetcher}: a JDK {@link HttpClient} GET or JSON POST asking for JSON. Logs and
 * error messages name the host and path only ({@link #redacted(URI, Collection)}), never the query
 * string, so an {@code app_key} sent as a parameter does not end up in a log; a key a partner takes
 * as a path segment (Jooble) is listed in the request's secrets and masked there too.
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
        return send(DumJobRequest.get(uri, headers));
    }

    @Override
    public String send(DumJobRequest request) {
        HttpRequest.Builder http = HttpRequest.newBuilder()
                .uri(request.uri())
                .timeout(Duration.ofMinutes(1))
                .header("Accept", "application/json");
        if (DumJobRequest.POST.equals(request.method())) {
            http.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            request.body() == null ? "" : request.body(), StandardCharsets.UTF_8));
        } else {
            http.GET();
        }
        request.headers().forEach(http::header);
        Set<String> secrets = request.secrets();
        String where = request.method() + " " + redacted(request.uri(), secrets);
        try {
            HttpResponse<String> response =
                    httpClient.send(http.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new DumJobFetchException("HTTP " + response.statusCode() + " from " + where);
            }
            log.debug("Fetched {} ({} bytes)", where, response.body().length());
            return response.body();
        } catch (IOException e) {
            throw new DumJobFetchException(
                    where + " failed: " + scrub(String.valueOf(e.getMessage()), secrets), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DumJobFetchException(where + " was interrupted", e);
        }
    }

    /** {@code uri} without its query string or user info — safe to log. */
    public static String redacted(URI uri) {
        return redacted(uri, Set.of());
    }

    /**
     * {@code uri} without its query string or user info, and with every one of {@code secrets} in its
     * path (as sent, percent-encoded, or decoded) replaced by {@value #MASK} — safe to log.
     */
    public static String redacted(URI uri, Collection<String> secrets) {
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
                + scrub(path, secrets);
    }

    /** {@code text} with every one of {@code secrets} (raw or percent-encoded) masked. */
    public static String scrub(String text, Collection<String> secrets) {
        if (text == null || secrets == null) {
            return text;
        }
        String scrubbed = text;
        for (String secret : secrets) {
            if (secret == null || secret.isBlank()) {
                continue;
            }
            scrubbed = scrubbed.replace(secret, MASK);
            String encoded = URLEncoder.encode(secret, StandardCharsets.UTF_8).replace("+", "%20");
            scrubbed = scrubbed.replace(encoded, MASK);
        }
        return scrubbed;
    }

    /** What a masked secret reads as. */
    public static final String MASK = "***";
}
