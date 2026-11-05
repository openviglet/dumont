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

package com.viglet.dumont.connector.commons.http;

import java.io.Closeable;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

/**
 * How every outbound HTTP fetcher backs off when a server asks it to (roadmap <strong>D315</strong>):
 * a {@code 429} or a {@code 503} is retried after the {@code Retry-After} it carries, a bounded
 * number of times and never longer than {@link #MAX_WAIT} per wait, so a server that never recovers
 * fails the call instead of holding the run. Any other status is handed back as it came, for the
 * caller to judge.
 *
 * <p>
 * One policy, shared by the JSON API, OAI-PMH and full-text fetchers, so a provider's rate limit
 * reads the same whichever source meets it. The wait goes through a {@link Sleeper}, so a test
 * records it instead of spending it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public final class DumHttpRetry {

    /** How many times a 429/503 is retried before the response is handed back. */
    public static final int DEFAULT_MAX_RETRIES = 3;
    /** The longest single wait, whatever {@code Retry-After} asks for. */
    public static final Duration MAX_WAIT = Duration.ofMinutes(5);
    /** The wait when a 429/503 carries no usable {@code Retry-After}. */
    public static final Duration DEFAULT_WAIT = Duration.ofSeconds(10);

    private final Sleeper sleeper;
    private final int maxRetries;

    /** Sleeps on the calling thread, {@link #DEFAULT_MAX_RETRIES} times at most. */
    public DumHttpRetry() {
        this(Thread::sleep, DEFAULT_MAX_RETRIES);
    }

    /**
     * @param sleeper    how a wait is spent; a test passes one that only records it
     * @param maxRetries how many times a 429/503 is retried, {@code 0} for none
     */
    public DumHttpRetry(Sleeper sleeper, int maxRetries) {
        this.sleeper = sleeper == null ? Thread::sleep : sleeper;
        this.maxRetries = Math.max(0, maxRetries);
    }

    /**
     * Sends {@code request}, waiting and sending it again while the server answers 429 or 503 and
     * retries remain. The last response is returned whatever its status; a discarded one has its
     * body closed when the handler streams it.
     */
    public <T> HttpResponse<T> send(HttpClient client, HttpRequest request,
            HttpResponse.BodyHandler<T> handler) throws IOException, InterruptedException {
        for (int attempt = 0;; attempt++) {
            HttpResponse<T> response = client.send(request, handler);
            int status = response.statusCode();
            if (!isBackOff(status) || attempt >= maxRetries) {
                return response;
            }
            discard(response.body());
            Duration wait = retryAfter(response.headers().firstValue("Retry-After"));
            log.info("{} answered HTTP {}; retrying in {}s", redact(request.uri().toString()), status,
                    wait.toSeconds());
            sleeper.sleep(wait.toMillis());
        }
    }

    /** Whether {@code status} is a server asking the client to come back later. */
    public static boolean isBackOff(int status) {
        return status == 429 || status == 503;
    }

    /**
     * The wait a {@code Retry-After} asks for, in its delta-seconds form, capped at
     * {@link #MAX_WAIT}. An HTTP-date or a missing header falls back to {@link #DEFAULT_WAIT}.
     */
    public static Duration retryAfter(Optional<String> header) {
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

    /** The URL without its query, which is where an API key or a resumption token travels. */
    public static String redact(String url) {
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query) + "?…";
    }

    private static void discard(Object body) {
        if (body instanceof Closeable closeable) {
            try {
                closeable.close();
            } catch (IOException e) {
                log.debug("Closing a discarded response body failed: {}", e.getMessage());
            }
        }
    }

    /** How a wait between retries is spent. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
