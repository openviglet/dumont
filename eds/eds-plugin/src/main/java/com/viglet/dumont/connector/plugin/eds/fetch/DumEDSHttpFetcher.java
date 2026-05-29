/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.plugin.eds.fetch;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.plugin.eds.discovery.DumEDSIndexClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Fetches a single HTML page with rate limiting and exponential-backoff retry.
 * Uses the JDK's {@link HttpClient} directly (rather than {@link
 * org.springframework.web.client.RestClient}) so the response headers
 * — specifically {@code Last-Modified} — are easy to read alongside the body.
 *
 * <p>Retries trigger on HTTP {@code 408}, {@code 425}, {@code 429}, and any
 * {@code 5xx}. Backoff: {@code 1s * 2^attempt} plus 0–500ms jitter. Max 3
 * retries by default.
 *
 * <p>{@code 404} and {@code 410} are treated as a permanent "gone" signal —
 * surfaced as {@link NotFoundException} so {@link
 * com.viglet.dumont.connector.plugin.eds.DumEDSPluginProcess} can emit a
 * DELETE for the (previously indexed) URL.
 *
 * <p>Redirects are followed transparently by the JDK client. When the
 * response's final URI differs from the request URI, a log line records the
 * hop so operators can audit redirect chains.
 */
@Slf4j
@Component
public class DumEDSHttpFetcher {

    public static final int DEFAULT_MAX_RETRIES = 3;
    public static final long BASE_BACKOFF_MS = 1_000L;
    public static final long MAX_JITTER_MS = 500L;

    public static final int HTTP_NOT_FOUND = 404;
    public static final int HTTP_GONE = 410;

    public record FetchResult(Document document, Optional<String> lastModified) {
    }

    public record Settings(
            String userAgent,
            int timeoutMs,
            double permitsPerSecond,
            int maxRetries) {

        public Settings withDefaults() {
            return new Settings(
                    userAgent == null || userAgent.isBlank()
                            ? DumEDSIndexClient.DEFAULT_USER_AGENT
                            : userAgent,
                    timeoutMs <= 0 ? 10_000 : timeoutMs,
                    permitsPerSecond <= 0 ? 50d : permitsPerSecond,
                    maxRetries < 0 ? DEFAULT_MAX_RETRIES : maxRetries);
        }
    }

    private static final Set<Integer> RETRYABLE_4XX = Set.of(408, 425, 429);

    private final HttpClient httpClient;

    public DumEDSHttpFetcher() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    DumEDSHttpFetcher(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public FetchResult fetch(URI url, Settings rawSettings, DumEDSRateLimiter rateLimiter)
            throws FetchException {
        Settings settings = rawSettings.withDefaults();
        FetchException lastError = null;
        for (int attempt = 0; attempt <= settings.maxRetries(); attempt++) {
            rateLimiter.acquire();
            try {
                return doSendAndClassify(url, settings);
            } catch (RetryableFetchException e) {
                lastError = e;
                logRetry(e.getMessage(), url, attempt, settings.maxRetries());
            } catch (FetchException e) {
                // NotFoundException is a FetchException subclass — surfaces here unchanged.
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FetchException("Interrupted while fetching " + url, e);
            } catch (Exception e) {
                lastError = new RetryableFetchException("I/O failure for " + url, e);
                logRetry(e.getMessage(), url, attempt, settings.maxRetries());
            }
            if (attempt < settings.maxRetries()) {
                sleepBackoff(attempt);
            }
        }
        throw lastError != null ? lastError
                : new FetchException("Retries exhausted for " + url);
    }

    private FetchResult doSendAndClassify(URI url, Settings settings)
            throws IOException, InterruptedException {
        HttpResponse<String> response = sendOnce(url, settings);
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            logRedirectIfAny(url, response.uri());
            return new FetchResult(Jsoup.parse(response.body(), url.toString()),
                    response.headers().firstValue("Last-Modified"));
        }
        if (status == HTTP_NOT_FOUND || status == HTTP_GONE) {
            throw new NotFoundException(url, status);
        }
        if (isRetryable(status)) {
            throw new RetryableFetchException("HTTP " + status + " for " + url);
        }
        throw new FetchException("HTTP " + status + " for " + url);
    }

    private HttpResponse<String> sendOnce(URI url, Settings settings)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(Duration.ofMillis(settings.timeoutMs()))
                .header("User-Agent", settings.userAgent())
                .header("Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    static boolean isRetryable(int status) {
        return RETRYABLE_4XX.contains(status) || (status >= 500 && status <= 599);
    }

    static void sleepBackoff(int attempt) {
        long base = BASE_BACKOFF_MS << attempt;
        long jitter = ThreadLocalRandom.current().nextLong(MAX_JITTER_MS + 1);
        long total = base + jitter;
        try {
            Thread.sleep(total);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void logRetry(String message, URI url, int attempt, int maxRetries) {
        // The message commonly already names the URL ("HTTP X for {url}"); we still
        // pass {@code url} so structured-logging consumers can index on it.
        log.warn("{} (attempt {}/{}, url={}), backing off",
                message, attempt + 1, maxRetries + 1, url);
    }

    private static void logRedirectIfAny(URI requested, URI finalUri) {
        if (finalUri != null && !finalUri.equals(requested)) {
            log.info("Followed redirect: {} → {}", requested, finalUri);
        }
    }

    public static class FetchException extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        public FetchException(String message) {
            super(message);
        }

        public FetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Signals that the target URL is permanently gone (HTTP 404 / 410). Not
     * retried — callers should remove the resource from the index.
     */
    public static class NotFoundException extends FetchException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;
        private final int status;

        public NotFoundException(URI url, int status) {
            super("HTTP " + status + " for " + url);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    /**
     * Internal control-flow marker for the retry loop. Never escapes the
     * fetcher — it is either retried or converted into a plain {@link
     * FetchException} when retries are exhausted.
     */
    private static class RetryableFetchException extends FetchException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        RetryableFetchException(String message) {
            super(message);
        }

        RetryableFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
