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
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

import com.viglet.dumont.connector.commons.http.DumHttpRetry;
import com.viglet.dumont.connector.commons.http.DumPacer;
import com.viglet.dumont.connector.commons.text.DumTextExtractor;

import lombok.extern.slf4j.Slf4j;

/**
 * The JDK client the DSpace profile (roadmap <strong>D314</strong>) reads the REST API with.
 *
 * <p>
 * Same host only, as {@link DumHttpOaiPmhFullTextFetcher} is: redirects — {@code pid/find}
 * answers with one — are followed by hand, at most {@value DumHttpOaiPmhFullTextFetcher#MAX_REDIRECTS},
 * and one that leaves the host ends the call. Every request is spaced
 * {@link #DEFAULT_MIN_INTERVAL} apart through a {@link DumPacer}, a 429/503 is waited out through
 * {@link DumHttpRetry}, and bitstream content goes through the shared {@link DumTextExtractor} with
 * the full-text fetcher's caps.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public final class DumHttpDSpaceRest implements DumDSpaceRest {

    /** The least time between two REST calls. */
    public static final Duration DEFAULT_MIN_INTERVAL = Duration.ofMillis(500);

    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);

    private final HttpClient client;
    private final DumHttpRetry retry;
    private final DumPacer pacer;
    private final long minIntervalMs;
    private final DumTextExtractor extractor;

    public DumHttpDSpaceRest() {
        this(HttpClient.newBuilder().connectTimeout(DumHttpOaiPmhFetcher.CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build(), new DumHttpRetry(),
                DumPacer.sleeping(), DEFAULT_MIN_INTERVAL,
                DumTextExtractor.builder().maxBytes(DumHttpOaiPmhFullTextFetcher.DEFAULT_MAX_BYTES)
                        .maxChars(DumHttpOaiPmhFullTextFetcher.DEFAULT_MAX_CHARS).build());
    }

    /**
     * @param client a client that does <em>not</em> follow redirects itself, or the host check is
     *               bypassed
     */
    public DumHttpDSpaceRest(HttpClient client, DumHttpRetry retry, DumPacer pacer, Duration minInterval,
            DumTextExtractor extractor) {
        this.client = client;
        this.retry = retry;
        this.pacer = pacer;
        this.minIntervalMs = minInterval.toMillis();
        this.extractor = extractor;
    }

    @Override
    public Response get(String url, String host) throws IOException {
        HttpResponse<InputStream> response = follow(url, host, "application/json");
        try (InputStream body = response.body()) {
            return new Response(response.statusCode(), new String(body.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Override
    public Optional<String> text(String url, String host) throws IOException {
        HttpResponse<InputStream> response = follow(url, host, "text/plain, application/pdf, */*;q=0.1");
        URI uri = response.uri();
        try (InputStream body = response.body()) {
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP %d from %s".formatted(response.statusCode(),
                        DumHttpRetry.redact(uri.toString())));
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("")
                    .toLowerCase(Locale.ROOT);
            if (!contentType.startsWith("text/plain")
                    && !DumHttpOaiPmhFullTextFetcher.isPdf(contentType, uri.getPath())) {
                log.debug("No text at {}: served as '{}'", DumHttpRetry.redact(uri.toString()), contentType);
                return Optional.empty();
            }
            OptionalLong length = response.headers().firstValueAsLong("Content-Length");
            if (length.isPresent() && length.getAsLong() > extractor.maxBytes()) {
                log.info("Not reading {}: {} bytes is over the cap of {}", DumHttpRetry.redact(uri.toString()),
                        length.getAsLong(), extractor.maxBytes());
                return Optional.empty();
            }
            DumTextExtractor.Extraction extraction = extractor.extract(body);
            return extraction.hasText() ? Optional.of(extraction.text().strip()) : Optional.empty();
        }
    }

    /** Sends the request, following same-host redirects; the last response is returned open. */
    private HttpResponse<InputStream> follow(String url, String host, String accept) throws IOException {
        URI uri = URI.create(url);
        for (int hop = 0; hop <= DumHttpOaiPmhFullTextFetcher.MAX_REDIRECTS; hop++) {
            if (!DumHttpOaiPmhFullTextFetcher.onHost(uri, host)) {
                throw new IOException("%s is not on %s".formatted(DumHttpRetry.redact(uri.toString()), host));
            }
            HttpResponse<InputStream> response = send(uri, accept);
            int status = response.statusCode();
            if (status / 100 != 3) {
                return response;
            }
            Optional<String> location = response.headers().firstValue("Location");
            response.body().close();
            if (location.isEmpty()) {
                throw new IOException("HTTP %d without a Location from %s".formatted(status,
                        DumHttpRetry.redact(uri.toString())));
            }
            uri = uri.resolve(location.get().strip());
        }
        throw new IOException("more than %d redirects from %s".formatted(DumHttpOaiPmhFullTextFetcher.MAX_REDIRECTS,
                DumHttpRetry.redact(url)));
    }

    private HttpResponse<InputStream> send(URI uri, String accept) throws IOException {
        pacer.awaitTurn(minIntervalMs);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT)
                .header("User-Agent", DumHttpOaiPmhFetcher.USER_AGENT)
                .header("Accept", accept)
                .GET().build();
        try {
            return retry.send(client, request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted fetching " + DumHttpRetry.redact(uri.toString()), e);
        }
    }
}
