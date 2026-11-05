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

package com.viglet.dumont.connector.commons.spi.structured.oaipmh;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

/**
 * The JDK {@link HttpClient} behind a {@link DumOaiPmhSource} (roadmap <strong>D315</strong>).
 *
 * <p>
 * It names itself with a fixed {@code User-Agent}, so a repository operator reading the access log
 * can tell who harvests them. A {@code 429} or {@code 503} is how OAI-PMH 2.0 asks a harvester to
 * back off (section 3.1.2.1 of the specification), usually with a {@code Retry-After} in seconds:
 * the fetcher waits that long and asks again, a bounded number of times and never longer than
 * {@link #MAX_WAIT} per wait, so a repository that never recovers fails the call instead of holding
 * the run. Any other non-2xx status fails at once, so an error page is never parsed as a response.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public final class DumHttpOaiPmhFetcher implements DumOaiPmhFetcher {

    /** What the harvester calls itself to the repository. */
    public static final String USER_AGENT = "Viglet Dumont OAI-PMH harvester";
    /** How many times a 429/503 is retried before the call fails. */
    public static final int DEFAULT_MAX_RETRIES = 3;
    /** The longest single wait, whatever {@code Retry-After} asks for. */
    public static final Duration MAX_WAIT = Duration.ofMinutes(5);
    /** The wait when a 429/503 carries no usable {@code Retry-After}. */
    static final Duration DEFAULT_WAIT = Duration.ofSeconds(10);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);

    private final HttpClient client;
    private final Sleeper sleeper;
    private final int maxRetries;

    public DumHttpOaiPmhFetcher() {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL).build(), Thread::sleep,
                DEFAULT_MAX_RETRIES);
    }

    /**
     * @param sleeper    how a wait is spent; a test passes one that only records it
     * @param maxRetries how many times a 429/503 is retried, {@code 0} for none
     */
    public DumHttpOaiPmhFetcher(HttpClient client, Sleeper sleeper, int maxRetries) {
        this.client = client;
        this.sleeper = sleeper;
        this.maxRetries = Math.max(0, maxRetries);
    }

    @Override
    public String fetch(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/xml, application/xml")
                .GET().build();
        try {
            for (int attempt = 0;; attempt++) {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status / 100 == 2) {
                    return response.body();
                }
                if ((status == 429 || status == 503) && attempt < maxRetries) {
                    Duration wait = retryAfter(response.headers().firstValue("Retry-After"));
                    log.info("OAI-PMH repository answered HTTP {} for {}; retrying in {}s", status,
                            redact(url), wait.toSeconds());
                    sleeper.sleep(wait.toMillis());
                    continue;
                }
                throw new IOException("HTTP %d from %s".formatted(status, redact(url)));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted fetching " + redact(url), e);
        }
    }

    /**
     * The wait a {@code Retry-After} asks for, in its delta-seconds form, capped at
     * {@link #MAX_WAIT}. An HTTP-date or a missing header falls back to {@link #DEFAULT_WAIT}.
     */
    static Duration retryAfter(Optional<String> header) {
        if (header.isPresent()) {
            try {
                long seconds = Long.parseLong(header.get().trim());
                if (seconds >= 0) {
                    Duration wait = Duration.ofSeconds(seconds);
                    return wait.compareTo(MAX_WAIT) > 0 ? MAX_WAIT : wait;
                }
            } catch (NumberFormatException e) {
                // An HTTP-date: not worth a clock dependency for a back-off hint.
            }
        }
        return DEFAULT_WAIT;
    }

    /** The URL without its query: a resumption token is noise in a log line. */
    static String redact(String url) {
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query) + "?…";
    }

    /** How the fetcher waits between retries. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
