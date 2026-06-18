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

package com.viglet.dumont.connector.aem.commons.ext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.bean.DumAemAttrMap;

/**
 * AEM extractor concurrency contract + statelessness gate (roadmap <strong>D32</strong>).
 *
 * <p>
 * D31 extracts ids in parallel ({@code DumBoundedExtraction}) and the content extractor is a
 * <strong>cached singleton</strong> ({@code DumCustomClassCache}) reused across threads — so one
 * {@link DumAemExtModelJsonBase} / {@link DumAemComponentExtractor} instance has its extraction
 * methods invoked concurrently under {@code dumont.reactive.indexing=true}. The exposure equals the
 * legacy reactive navigator's (same singleton, same per-page calls), and this harness locks the
 * <em>stateless-during-extraction</em> contract documented on {@link DumAemComponentExtractor}.
 *
 * <p>
 * Each test drives a representative shipped extractor style — the composed component SPI (D24), the
 * content-type-routed model (D25), and a monolithic single-method extractor (the Insper god-class
 * shape, which lives in a separate plugin module and so is mirrored here) — through <em>sequential
 * vs parallel</em> extraction over the <em>same</em> singleton instance and the same inputs, then
 * asserts byte-for-byte identical {@link DumAemAttrMap}s. A deliberately stateful extractor that
 * stashes per-page state in an instance field is shown to diverge under parallel extraction, proving
 * the harness catches a contract violation.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@DisplayName("AEM extractor concurrency contract (D32): parallel extraction == sequential extraction")
class DumAemExtractorConcurrencyTest {

    /** Enough distinct pages to make scheduling interleave; also the worker count and barrier size. */
    private static final int PAGES = 16;
    private static final long JOIN_TIMEOUT_SECONDS = 30;

    @Test
    @DisplayName("Composed component-extractor SPI (D24) is concurrency-safe")
    void composedExtractorIsConcurrencySafe() throws Exception {
        ComposedReferenceExtractor extractor = new ComposedReferenceExtractor();
        assertParallelMatchesSequential(extractor::extract, pageModels());
    }

    @Test
    @DisplayName("Content-type-routed model (D25) is concurrency-safe")
    void routedExtractorIsConcurrencySafe() throws Exception {
        RoutedReferenceExtractor extractor = new RoutedReferenceExtractor();
        assertParallelMatchesSequential(extractor::extract, routedModels());
    }

    @Test
    @DisplayName("Monolithic single-method extractor (Insper shape) is concurrency-safe")
    void monolithicExtractorIsConcurrencySafe() throws Exception {
        MonolithicReferenceExtractor extractor = new MonolithicReferenceExtractor();
        assertParallelMatchesSequential(extractor::extract, pageModels());
    }

    @Test
    @DisplayName("A deliberately stateful extractor is caught: parallel diverges from sequential")
    void statefulExtractorIsCaught() throws Exception {
        List<String> models = pageModels();
        StatefulMonolithicExtractor extractor = new StatefulMonolithicExtractor();

        // Sequential first, while the rendezvous barrier is disengaged: no interleave, so the
        // per-call instance field is read back intact — this is the correct baseline.
        Map<String, DumAemAttrMap> sequential = sequential(extractor::extract, models);
        models.forEach(model -> assertEquals(model, sequential.get(model).get("echo").getFirst(),
                "sequential extraction must be correct — the bug only manifests under concurrency"));

        // Now force every worker to rendezvous between the field write and read, so each thread reads
        // a value another thread overwrote — the corruption the contract forbids.
        extractor.interleave = new CyclicBarrier(models.size());
        Map<String, DumAemAttrMap> parallel = parallel(extractor::extract, models);

        assertNotEquals(sequential, parallel,
                "the harness must catch a stateful extractor — parallel output should differ");
    }

    // --- Harness ----------------------------------------------------------------------------------

    /**
     * The contract this test enforces: extracting every page sequentially and in parallel over the
     * <em>same</em> singleton instance yields identical attribute maps.
     */
    private void assertParallelMatchesSequential(Function<String, DumAemAttrMap> extract,
            List<String> models) throws Exception {
        Map<String, DumAemAttrMap> sequential = sequential(extract, models);
        Map<String, DumAemAttrMap> parallel = parallel(extract, models);

        assertFalse(sequential.isEmpty(), "expected the harness to extract real pages");
        assertTrue(sequential.values().stream().noneMatch(Map::isEmpty),
                "every page should extract non-empty attributes");
        assertEquals(sequential, parallel, "parallel extraction must equal sequential extraction");
    }

    private Map<String, DumAemAttrMap> sequential(Function<String, DumAemAttrMap> extract,
            List<String> models) {
        Map<String, DumAemAttrMap> out = new LinkedHashMap<>();
        models.forEach(model -> out.put(model, extract.apply(model)));
        return out;
    }

    /**
     * Extracts every page on its own thread, released together by a start gate so the calls genuinely
     * overlap on the single shared extractor instance.
     */
    private Map<String, DumAemAttrMap> parallel(Function<String, DumAemAttrMap> extract,
            List<String> models) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(models.size());
        CountDownLatch startGate = new CountDownLatch(1);
        Map<String, DumAemAttrMap> out = new ConcurrentHashMap<>();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (String model : models) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    out.put(model, extract.apply(model));
                    return null;
                }));
            }
            startGate.countDown();
            for (Future<?> future : futures) {
                future.get(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        return out;
    }

    private List<String> pageModels() {
        return IntStream.range(0, PAGES).mapToObj(i -> "page-" + i).toList();
    }

    /** Half route to the {@code article} type, half to {@code event} — to exercise routing concurrently. */
    private List<String> routedModels() {
        return IntStream.range(0, PAGES)
                .mapToObj(i -> (i % 2 == 0 ? "news-" : "event-") + i)
                .toList();
    }

    // --- Representative extractors (stateless — the shipped contract) -----------------------------

    /** D24 shape: extraction expressed purely as composed, stateless component extractors. */
    private static final class ComposedReferenceExtractor extends DumAemExtModelJsonBase<String> {
        @Override
        protected Class<String> getModelClass() {
            return String.class;
        }

        @Override
        protected List<DumAemComponentExtractor<String>> componentExtractors() {
            return List.of(
                    (model, query, aemObject, attrs) -> attrs.set("title", model + "-title"),
                    (model, query, aemObject, attrs) ->
                            attrs.append("tags", model + "-a").append("tags", model + "-b"),
                    (model, query, aemObject, attrs) -> attrs.set("body", model.toUpperCase()));
        }

        DumAemAttrMap extract(String model) {
            DumAemAttrMap attrs = new DumAemAttrMap();
            extractAttributes(model, null, null, attrs);
            return attrs;
        }
    }

    /** D25 shape: a template (the model string's prefix) routes to a per-type extractor composition. */
    private static final class RoutedReferenceExtractor extends DumAemExtModelJsonBase<String> {
        @Override
        protected Class<String> getModelClass() {
            return String.class;
        }

        @Override
        protected Map<String, String> templateNameAliases() {
            return Map.of("news", "article", "event", "event");
        }

        @Override
        protected String templateName(String model, DumAemObject aemObject) {
            int dash = model.indexOf('-');
            return dash < 0 ? model : model.substring(0, dash);
        }

        @Override
        protected List<DumAemContentType<String>> contentTypes() {
            return List.of(
                    DumAemContentType.<String>builder("article")
                            .field(DumAemContentTypeField.text("articleBody"))
                            .extractor((model, query, aemObject, attrs) ->
                                    attrs.set("articleBody", model + "-article"))
                            .build(),
                    DumAemContentType.<String>builder("event")
                            .field(DumAemContentTypeField.text("eventDate"))
                            .extractor((model, query, aemObject, attrs) ->
                                    attrs.set("eventDate", model + "-event"))
                            .build());
        }

        DumAemAttrMap extract(String model) {
            DumAemAttrMap attrs = new DumAemAttrMap();
            extractAttributes(model, null, null, attrs);
            return attrs;
        }
    }

    /** Insper shape: one monolithic {@code extractAttributes}, but correctly written with only locals. */
    private static final class MonolithicReferenceExtractor extends DumAemExtModelJsonBase<String> {
        @Override
        protected Class<String> getModelClass() {
            return String.class;
        }

        @Override
        protected void extractAttributes(String model, DumAemModelJsonQuery query,
                DumAemObject aemObject, DumAemAttrMap attrs) {
            String slug = model.replace("page-", "");
            attrs.set("title", model + "-title");
            attrs.set("slug", slug);
            attrs.append("tags", "x").append("tags", "y");
        }

        DumAemAttrMap extract(String model) {
            DumAemAttrMap attrs = new DumAemAttrMap();
            extractAttributes(model, null, null, attrs);
            return attrs;
        }
    }

    // --- The contract violation (must be caught) --------------------------------------------------

    /**
     * A deliberately stateful extractor: it stashes the per-page value in an instance field
     * <em>between</em> write and read — the exact pattern the contract forbids. When the optional
     * {@code interleave} barrier is engaged, concurrent calls rendezvous between the write and the
     * read, so each thread reads a value another thread overwrote and the output is corrupted.
     */
    private static final class StatefulMonolithicExtractor extends DumAemExtModelJsonBase<String> {
        /** Per-invocation mutable instance state — the violation. */
        private volatile String pending;
        /** Set by the harness to force concurrent interleaving; null disables it (sequential run). */
        volatile CyclicBarrier interleave;

        @Override
        protected Class<String> getModelClass() {
            return String.class;
        }

        @Override
        protected void extractAttributes(String model, DumAemModelJsonQuery query,
                DumAemObject aemObject, DumAemAttrMap attrs) {
            this.pending = model;
            await(interleave);
            attrs.set("echo", this.pending);
        }

        DumAemAttrMap extract(String model) {
            DumAemAttrMap attrs = new DumAemAttrMap();
            extractAttributes(model, null, null, attrs);
            return attrs;
        }

        private static void await(CyclicBarrier barrier) {
            if (barrier == null) {
                return;
            }
            try {
                barrier.await(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (BrokenBarrierException | java.util.concurrent.TimeoutException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
