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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.source.expiry.DumJobLinkChecker.Response;
import com.viglet.dumont.connector.plugin.jobs.source.expiry.DumJobLinkChecker.Verdict;

/** HEAD verdicts, redirects, the rate limit and the sample setting (trabalharei TRA24). */
class DumJobLinkCheckerTest {

    /** A scripted site: an answer per URL (200 when unscripted), every request recorded. */
    static final class ScriptedTransport implements DumJobLinkChecker.Transport {
        final Map<String, Object> answers = new HashMap<>();
        final List<String> requested = new ArrayList<>();

        ScriptedTransport answer(String url, int status) {
            answers.put(url, new Response(status, null));
            return this;
        }

        ScriptedTransport redirect(String url, int status, String location) {
            answers.put(url, new Response(status, location));
            return this;
        }

        ScriptedTransport fail(String url) {
            answers.put(url, new IOException("connection reset"));
            return this;
        }

        @Override
        public Response head(URI uri) throws IOException {
            requested.add(uri.toString());
            Object answer = answers.getOrDefault(uri.toString(), new Response(200, null));
            if (answer instanceof IOException e) {
                throw e;
            }
            return (Response) answer;
        }
    }

    private static DumJobLinkChecker checker(ScriptedTransport transport) {
        return new DumJobLinkChecker(20, new DumJobRateLimiter(0), transport);
    }

    @Test
    void notFoundAndGoneAreDeadEverythingElseIsNot() {
        ScriptedTransport site = new ScriptedTransport().answer("https://a.example/404", 404)
                .answer("https://a.example/410", 410).answer("https://a.example/500", 500)
                .answer("https://a.example/503", 503).answer("https://a.example/429", 429)
                .answer("https://a.example/408", 408).answer("https://a.example/403", 403)
                .answer("https://a.example/405", 405).fail("https://a.example/reset");
        DumJobLinkChecker checker = checker(site);

        assertThat(checker.check("https://a.example/404")).isEqualTo(Verdict.DEAD);
        assertThat(checker.check("https://a.example/410")).isEqualTo(Verdict.DEAD);
        assertThat(checker.check("https://a.example/500")).isEqualTo(Verdict.RETRY);
        assertThat(checker.check("https://a.example/503")).isEqualTo(Verdict.RETRY);
        assertThat(checker.check("https://a.example/429")).isEqualTo(Verdict.RETRY);
        assertThat(checker.check("https://a.example/408")).isEqualTo(Verdict.RETRY);
        assertThat(checker.check("https://a.example/reset")).isEqualTo(Verdict.RETRY);
        // A site that refuses robots or HEAD gives no evidence the posting is gone.
        assertThat(checker.check("https://a.example/403")).isEqualTo(Verdict.ALIVE);
        assertThat(checker.check("https://a.example/405")).isEqualTo(Verdict.ALIVE);
        assertThat(checker.check("https://a.example/ok")).isEqualTo(Verdict.ALIVE);
        assertThat(checker.check("not a url at all")).isEqualTo(Verdict.ALIVE);
        assertThat(checker.check("mailto:rh@acme.example")).isEqualTo(Verdict.ALIVE);
    }

    @Test
    void redirectsAreFollowedUpToThreeAndTheEndOfTheChainDecides() {
        ScriptedTransport site = new ScriptedTransport()
                .redirect("https://board.example/job/1", 301, "https://ats.example/v/1")
                .redirect("https://ats.example/v/1", 302, "/v/1/apply")
                .answer("https://ats.example/v/1/apply", 404)
                .redirect("https://board.example/job/2", 307, "https://ats.example/v/2")
                .answer("https://ats.example/v/2", 200)
                .redirect("https://loop.example/1", 302, "https://loop.example/2")
                .redirect("https://loop.example/2", 302, "https://loop.example/3")
                .redirect("https://loop.example/3", 302, "https://loop.example/4")
                .redirect("https://loop.example/4", 302, "https://loop.example/5")
                .answer("https://loop.example/5", 404)
                .redirect("https://listing.example/job/9", 302, "https://listing.example/vagas?encerrada=1");
        DumJobLinkChecker checker = checker(site);

        // Followed, a relative Location resolved against the hop it came from.
        assertThat(checker.check("https://board.example/job/1")).isEqualTo(Verdict.DEAD);
        assertThat(site.requested).containsExactly("https://board.example/job/1", "https://ats.example/v/1",
                "https://ats.example/v/1/apply");
        assertThat(checker.check("https://board.example/job/2")).isEqualTo(Verdict.ALIVE);

        // Three redirects are followed; the fourth is not, and is no evidence of a dead link.
        site.requested.clear();
        assertThat(checker.check("https://loop.example/1")).isEqualTo(Verdict.ALIVE);
        assertThat(site.requested).containsExactly("https://loop.example/1", "https://loop.example/2",
                "https://loop.example/3", "https://loop.example/4");

        // A redirect to a listing or "vaga encerrada" page is not read as dead: only 404/410 are.
        assertThat(checker.check("https://listing.example/job/9")).isEqualTo(Verdict.ALIVE);
    }

    @Test
    void everyRequestHopIncludedWaitsForTheRateLimit() throws Exception {
        AtomicLong nanos = new AtomicLong(1_000L);
        List<Long> sleeps = new ArrayList<>();
        DumJobRateLimiter limiter = new DumJobRateLimiter(2, nanos::get, wait -> {
            sleeps.add(wait);
            nanos.addAndGet(wait);
        });
        ScriptedTransport site = new ScriptedTransport()
                .redirect("https://board.example/r", 302, "https://ats.example/r");
        List<Long> requestTimes = new ArrayList<>();
        DumJobLinkChecker checker = new DumJobLinkChecker(20, limiter, uri -> {
            requestTimes.add(nanos.get());
            return site.head(uri);
        });

        checker.check("https://a.example/1");
        checker.check("https://a.example/2");
        checker.check("https://board.example/r");
        checker.check("https://a.example/3");

        // Five requests at two a second: each one half a second after the previous.
        assertThat(requestTimes).hasSize(5);
        for (int i = 1; i < requestTimes.size(); i++) {
            assertThat(requestTimes.get(i) - requestTimes.get(i - 1)).isGreaterThanOrEqualTo(500_000_000L);
        }
        assertThat(sleeps).hasSize(4);
        assertThat(requestTimes.getLast() - requestTimes.getFirst()).isEqualTo(2_000_000_000L);
    }

    @Test
    void anIdleLimiterDoesNotWaitAndZeroMeansNoLimit() throws Exception {
        AtomicLong nanos = new AtomicLong();
        List<Long> sleeps = new ArrayList<>();
        DumJobRateLimiter limiter = new DumJobRateLimiter(2, nanos::get, sleeps::add);
        limiter.acquire();
        nanos.addAndGet(3_000_000_000L);
        limiter.acquire();
        assertThat(sleeps).isEmpty();

        DumJobRateLimiter unlimited = new DumJobRateLimiter(0, nanos::get, sleeps::add);
        for (int i = 0; i < 10; i++) {
            unlimited.acquire();
        }
        assertThat(sleeps).isEmpty();
    }

    @Test
    void theSampleSizeIsTheSettingElseTheSourceOverride() {
        DumJobLinkChecker checker = new DumJobLinkChecker(20, new DumJobRateLimiter(0), new ScriptedTransport());
        assertThat(checker.sampleSize()).isEqualTo(20);
        assertThat(checker.sampleSize(DumJobSource.builder().name("a").build())).isEqualTo(20);
        assertThat(checker.sampleSize(DumJobSource.builder().name("a")
                .settings("{\"linkCheck\":{\"sampleSize\":5}}").build())).isEqualTo(5);
        assertThat(checker.sampleSize(DumJobSource.builder().name("a")
                .settings("{\"linkCheck\":{\"sampleSize\":0}}").build())).isZero();
        assertThat(checker.sampleSize(DumJobSource.builder().name("a").settings("{\"maxPages\":3}").build()))
                .isEqualTo(20);
        assertThat(checker.sampleSize(DumJobSource.builder().name("a")
                .settings("{\"linkCheck\":{\"sampleSize\":\"many\"}}").build())).isEqualTo(20);
        assertThat(new DumJobLinkChecker(-3, new DumJobRateLimiter(0), new ScriptedTransport()).sampleSize())
                .isZero();
    }

    @Test
    void theHttpTransportSendsHeadAndDoesNotFollowRedirectsItself() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> seen = new ArrayList<>();
        server.createContext("/", exchange -> {
            seen.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            switch (exchange.getRequestURI().getPath()) {
                case "/moved" -> {
                    exchange.getResponseHeaders().add("Location", "/gone");
                    exchange.sendResponseHeaders(301, -1);
                }
                case "/gone" -> exchange.sendResponseHeaders(410, -1);
                default -> exchange.sendResponseHeaders(200, -1);
            }
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            DumJobLinkChecker checker = new DumJobLinkChecker(20, new DumJobRateLimiter(0),
                    DumJobLinkChecker.httpTransport());

            assertThat(checker.check(base + "/moved")).isEqualTo(Verdict.DEAD);
            assertThat(checker.check(base + "/open")).isEqualTo(Verdict.ALIVE);
            assertThat(seen).containsExactly("HEAD /moved", "HEAD /gone", "HEAD /open");
        } finally {
            server.stop(0);
        }
        // Nothing listens there any more: a network error, not a dead link.
        DumJobLinkChecker checker = new DumJobLinkChecker(20, new DumJobRateLimiter(0),
                DumJobLinkChecker.httpTransport());
        assertThat(checker.check("http://127.0.0.1:" + server.getAddress().getPort() + "/open"))
                .isEqualTo(Verdict.RETRY);
    }
}
