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

import com.viglet.dumont.connector.commons.http.DumHttpRetry;

/**
 * The JDK {@link HttpClient} behind a {@link DumOaiPmhSource} (roadmap <strong>D315</strong>).
 *
 * <p>
 * It names itself with a fixed {@code User-Agent}, so a repository operator reading the access log
 * can tell who harvests them. A {@code 429} or {@code 503} is how OAI-PMH 2.0 asks a harvester to
 * back off (section 3.1.2.1 of the specification), usually with a {@code Retry-After} in seconds:
 * the shared {@link DumHttpRetry} waits that long and asks again, a bounded number of times, so a
 * repository that never recovers fails the call instead of holding the run. Any other non-2xx
 * status fails at once, so an error page is never parsed as a response.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumHttpOaiPmhFetcher implements DumOaiPmhFetcher {

    /** What the harvester calls itself to the repository. */
    public static final String USER_AGENT = "Viglet Dumont OAI-PMH harvester";

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);

    private final HttpClient client;
    private final DumHttpRetry retry;

    public DumHttpOaiPmhFetcher() {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL).build(), new DumHttpRetry());
    }

    /** @param retry how a 429/503 is waited out; a test passes one with a recording sleeper */
    public DumHttpOaiPmhFetcher(HttpClient client, DumHttpRetry retry) {
        this.client = client;
        this.retry = retry;
    }

    @Override
    public String fetch(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/xml, application/xml")
                .GET().build();
        try {
            HttpResponse<String> response = retry.send(client, request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP %d from %s".formatted(response.statusCode(),
                        DumHttpRetry.redact(url)));
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted fetching " + DumHttpRetry.redact(url), e);
        }
    }
}
