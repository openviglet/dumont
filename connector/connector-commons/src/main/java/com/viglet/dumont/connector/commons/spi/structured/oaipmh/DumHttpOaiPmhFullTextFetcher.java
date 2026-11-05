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
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

import com.viglet.dumont.connector.commons.http.DumHttpRetry;
import com.viglet.dumont.connector.commons.http.DumPacer;
import com.viglet.dumont.connector.commons.text.DumTextExtractor;

import lombok.extern.slf4j.Slf4j;

/**
 * Downloads a record's PDF from the repository and extracts its text (roadmap <strong>D315</strong>).
 *
 * <p>
 * <strong>Same host only.</strong> Redirects are followed by hand, at most
 * {@value #MAX_REDIRECTS} of them, and one that leaves {@code host} ends the fetch: a harvester
 * pointed at one repository never downloads from anywhere else, whatever a record or a redirect
 * names.
 *
 * <p>
 * <strong>PDF only, and capped.</strong> A response is read only when it is served as
 * {@code application/pdf}, or as a generic binary from a path ending in {@code .pdf}; a landing
 * page is closed unread. The body goes through the shared {@link DumTextExtractor} with a
 * {@value #DEFAULT_MAX_BYTES}-byte (50 MiB) and {@value #DEFAULT_MAX_CHARS}-character cap, and a
 * declared {@code Content-Length} over the byte cap is refused before a byte is read.
 *
 * <p>
 * <strong>Polite.</strong> Downloads are spaced {@link #DEFAULT_MIN_INTERVAL} apart through a
 * {@link DumPacer}, and a 429/503 is waited out through the shared {@link DumHttpRetry}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public final class DumHttpOaiPmhFullTextFetcher implements DumOaiPmhFullTextFetcher {

    /** The byte cap on one document: 50 MiB. */
    public static final long DEFAULT_MAX_BYTES = 50L * 1024 * 1024;
    /** The character cap on one document's text: one million characters. */
    public static final int DEFAULT_MAX_CHARS = 1_000_000;
    /** The least time between two downloads. */
    public static final Duration DEFAULT_MIN_INTERVAL = Duration.ofSeconds(1);
    /** The most redirects one fetch follows. */
    public static final int MAX_REDIRECTS = 3;

    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);

    private final HttpClient client;
    private final DumHttpRetry retry;
    private final DumPacer pacer;
    private final long minIntervalMs;
    private final DumTextExtractor extractor;

    public DumHttpOaiPmhFullTextFetcher() {
        this(HttpClient.newBuilder().connectTimeout(DumHttpOaiPmhFetcher.CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build(), new DumHttpRetry(),
                DumPacer.sleeping(), DEFAULT_MIN_INTERVAL,
                DumTextExtractor.builder().maxBytes(DEFAULT_MAX_BYTES).maxChars(DEFAULT_MAX_CHARS).build());
    }

    /**
     * @param client a client that does <em>not</em> follow redirects itself, or the host check is
     *               bypassed
     */
    public DumHttpOaiPmhFullTextFetcher(HttpClient client, DumHttpRetry retry, DumPacer pacer,
            Duration minInterval, DumTextExtractor extractor) {
        this.client = client;
        this.retry = retry;
        this.pacer = pacer;
        this.minIntervalMs = minInterval.toMillis();
        this.extractor = extractor;
    }

    @Override
    public Optional<String> text(String url, String host) throws IOException {
        URI uri = URI.create(url);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!onHost(uri, host)) {
                log.info("Not fetching full text from {}: not on {}", DumHttpRetry.redact(uri.toString()), host);
                return Optional.empty();
            }
            HttpResponse<InputStream> response = send(uri);
            int status = response.statusCode();
            if (status / 100 == 3) {
                Optional<String> location = response.headers().firstValue("Location");
                response.body().close();
                if (location.isEmpty()) {
                    throw new IOException("HTTP %d without a Location from %s".formatted(status,
                            DumHttpRetry.redact(uri.toString())));
                }
                uri = uri.resolve(location.get().strip());
                continue;
            }
            try (InputStream body = response.body()) {
                if (status / 100 != 2) {
                    throw new IOException("HTTP %d from %s".formatted(status, DumHttpRetry.redact(uri.toString())));
                }
                return read(uri, response, body);
            }
        }
        log.info("Not fetching full text from {}: more than {} redirects", DumHttpRetry.redact(url), MAX_REDIRECTS);
        return Optional.empty();
    }

    private Optional<String> read(URI uri, HttpResponse<InputStream> response, InputStream body) {
        String contentType = response.headers().firstValue("Content-Type").orElse("")
                .toLowerCase(Locale.ROOT);
        if (!isPdf(contentType, uri.getPath())) {
            log.debug("No full text at {}: served as '{}'", DumHttpRetry.redact(uri.toString()), contentType);
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

    private HttpResponse<InputStream> send(URI uri) throws IOException {
        pacer.awaitTurn(minIntervalMs);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT)
                .header("User-Agent", DumHttpOaiPmhFetcher.USER_AGENT)
                .header("Accept", "application/pdf, */*;q=0.1")
                .GET().build();
        try {
            return retry.send(client, request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted fetching " + DumHttpRetry.redact(uri.toString()), e);
        }
    }

    /** Whether a response is a PDF: declared one, or a generic binary from a {@code .pdf} path. */
    static boolean isPdf(String contentType, String path) {
        if (contentType.startsWith("application/pdf")) {
            return true;
        }
        boolean pdfPath = path != null && path.toLowerCase(Locale.ROOT).endsWith(".pdf");
        return pdfPath && (contentType.isBlank() || contentType.startsWith("application/octet-stream"));
    }

    /** Whether {@code uri} is an http(s) URL on {@code host}, compared without case. */
    static boolean onHost(URI uri, String host) {
        String scheme = uri.getScheme();
        return host != null && uri.getHost() != null
                && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                && uri.getHost().equalsIgnoreCase(host);
    }
}
