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

package com.viglet.dumont.connector.plugin.jobs.source.expiry;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobHttpFetcher;

import lombok.extern.slf4j.Slf4j;

/**
 * Finds postings whose apply link is gone (trabalharei <strong>TRA24</strong>). Each run HEAD-checks a
 * small sample of a source's open postings; a dead link expires the posting at once instead of
 * leaving it listed until its date runs out.
 *
 * <p>
 * <strong>The verdict.</strong> Redirects are followed by hand, up to {@value #MAX_REDIRECTS}; the
 * answer at the end of the chain decides:
 * <ul>
 * <li>{@code 404} or {@code 410} &rarr; {@link Verdict#DEAD};</li>
 * <li>a network error, a {@code 5xx}, {@code 408} or {@code 429} &rarr; {@link Verdict#RETRY}: not dead,
 * and checked again on the next run;</li>
 * <li>anything else &mdash; a {@code 2xx}, a {@code 401/403/405} from a site that refuses robots or HEAD,
 * a fourth redirect, an unreadable URL &rarr; {@link Verdict#ALIVE}: no evidence the posting is
 * gone.</li>
 * </ul>
 * A redirect to a "vaga encerrada" or search-results page is <em>not</em> read as dead: every ATS and
 * board spells that page differently ({@code /vagas?expired=1}, {@code /jobs}, a home page, a login
 * wall), and a home-page redirect is also what a healthy tracking link can do. No rule that could be
 * written here tells those apart reliably, and a false "dead" hides a live opening, so only the two
 * status codes that mean "gone" count.
 *
 * <p>
 * <strong>Not partner API calls.</strong> The links point at employer and board sites, not at the
 * partner APIs, so checks are never charged to a source's call budget. They are spaced by a
 * {@link DumJobRateLimiter} shared by every source ({@code dumont.jobs.link-check.per-second}, default
 * {@value #DEFAULT_PER_SECOND}); every hop of a redirect chain takes a permit. Logs name the host and
 * path only ({@link DumJobHttpFetcher#redacted(URI)}): an apply link's query string may carry
 * tracking tokens.
 *
 * <p>
 * The sample size is {@code dumont.jobs.link-check.sample-size} (default {@value #DEFAULT_SAMPLE_SIZE}
 * per source per run), overridden per source by {@code {"linkCheck": {"sampleSize": n}}} in its
 * settings; {@code 0} turns checking off.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
@Slf4j
@Component
public class DumJobLinkChecker {

    public static final int DEFAULT_SAMPLE_SIZE = 20;
    public static final double DEFAULT_PER_SECOND = 2;
    public static final int MAX_REDIRECTS = 3;

    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);

    /** What a check concluded. */
    public enum Verdict {
        /** No evidence the posting is gone. */
        ALIVE,
        /** {@code 404} or {@code 410}: the posting is gone. */
        DEAD,
        /** A network error or a server-side failure: unknown, check again next run. */
        RETRY
    }

    /** One HEAD answer: the status and, for a redirect, its {@code Location}. */
    public record Response(int status, String location) {
    }

    /** One HEAD request, without following redirects. The seam lets tests run with no network. */
    @FunctionalInterface
    public interface Transport {
        Response head(URI uri) throws IOException, InterruptedException;
    }

    private final int sampleSize;
    private final DumJobRateLimiter limiter;
    private final Transport transport;

    @Autowired
    public DumJobLinkChecker(
            @Value("${dumont.jobs.link-check.sample-size:" + DEFAULT_SAMPLE_SIZE + "}") int sampleSize,
            @Value("${dumont.jobs.link-check.per-second:" + DEFAULT_PER_SECOND + "}") double perSecond) {
        this(sampleSize, new DumJobRateLimiter(perSecond), httpTransport());
    }

    public DumJobLinkChecker(int sampleSize, DumJobRateLimiter limiter, Transport transport) {
        this.sampleSize = Math.max(0, sampleSize);
        this.limiter = limiter;
        this.transport = transport;
    }

    /** The default sample size per source per run. */
    public int sampleSize() {
        return sampleSize;
    }

    /** The sample size for {@code source}: its {@code linkCheck.sampleSize} setting, else the default. */
    public int sampleSize(DumJobSource source) {
        String settings = source == null ? null : source.getSettings();
        if (settings == null || settings.isBlank()) {
            return sampleSize;
        }
        try {
            JSONObject linkCheck = new JSONObject(settings).optJSONObject("linkCheck");
            if (linkCheck != null && linkCheck.has("sampleSize")) {
                return Math.max(0, linkCheck.getInt("sampleSize"));
            }
        } catch (RuntimeException e) {
            log.warn("Job source '{}': unreadable linkCheck setting ({}), using the default sample of {}",
                    source.getName(), e.getMessage(), sampleSize);
        }
        return sampleSize;
    }

    /** HEAD-check {@code url}, following up to {@value #MAX_REDIRECTS} redirects. */
    public Verdict check(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (RuntimeException e) {
            return Verdict.ALIVE;
        }
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!isHttp(uri)) {
                return Verdict.ALIVE;
            }
            Response response;
            try {
                limiter.acquire();
                response = transport.head(uri);
            } catch (IOException e) {
                log.debug("Link check of {} failed: {}", DumJobHttpFetcher.redacted(uri),
                        e.getClass().getSimpleName());
                return Verdict.RETRY;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Verdict.RETRY;
            }
            int status = response.status();
            if (status == 404 || status == 410) {
                return Verdict.DEAD;
            }
            if (status >= 500 || status == 408 || status == 429) {
                return Verdict.RETRY;
            }
            if (!REDIRECTS.contains(status) || response.location() == null
                    || response.location().isBlank()) {
                return Verdict.ALIVE;
            }
            try {
                uri = uri.resolve(response.location().trim());
            } catch (RuntimeException e) {
                return Verdict.ALIVE;
            }
        }
        log.debug("Link check stopped after {} redirects at {}", MAX_REDIRECTS, DumJobHttpFetcher.redacted(uri));
        return Verdict.ALIVE;
    }

    private static boolean isHttp(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null;
    }

    /** HEAD over the JDK client, redirects not followed, body discarded. */
    static Transport httpTransport() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return uri -> {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Mozilla/5.0 (compatible; DumontJobsLinkCheck/1.0)")
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return new Response(response.statusCode(),
                    response.headers().firstValue("Location").orElse(null));
        };
    }
}
