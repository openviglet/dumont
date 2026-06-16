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

package com.viglet.dumont.connector.commons.spi.structured.enrich;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourcePlugin;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.sn.field.TurSNFieldName;

/**
 * End-to-end proof of the D04 enrichment stage: a structured source indexes through
 * {@link DumStructuredSourcePlugin} with an attached {@link DumEnrichmentChain} that (a) derives a
 * field deterministically (region from a state code) and (b) classifies a field via the LLM seam
 * (area from a description), with the LLM path behind config + cache — while the grounding contract
 * still holds.
 */
class DumStructuredEnrichmentPluginTest {

    private static final String SOURCE = "courses";

    @Test
    void enrichesADerivedFieldAndAnLlmClassifiedField() {
        CapturingContext context = new CapturingContext();
        AtomicInteger calls = new AtomicInteger();
        Plugin plugin = new Plugin(context, chain(true, calls));

        plugin.indexAll(SOURCE);

        assertEquals(3, context.items.size());

        // c1: derived region (SP -> Sudeste) and LLM-classified area (description was thin).
        // "area" is a facet (multi-valued) so the value rides as a single-element list.
        TurSNJobItem c1 = itemById(context, "c1");
        assertEquals("Sudeste", c1.getAttributes().get("region"), "derived field present");
        assertEquals(List.of("Exact Sciences"), c1.getAttributes().get("area"),
                "LLM-classified field present");

        // c2: source already declared "area" -> classifier must NOT override sourced data.
        TurSNJobItem c2 = itemById(context, "c2");
        assertEquals(List.of("Humanities"), c2.getAttributes().get("area"),
                "sourced value wins over the model");
        assertEquals("Nordeste", c2.getAttributes().get("region"));

        // The classifier ran for c1 and c3 only — c2 already declared "area", so no-overwrite
        // spared that call entirely.
        assertEquals(2, calls.get(), "classifier skipped for the record that already had the field");
    }

    @Test
    void llmPathIsBehindConfig() {
        CapturingContext context = new CapturingContext();
        AtomicInteger calls = new AtomicInteger();
        Plugin plugin = new Plugin(context, chain(false, calls)); // LLM disabled

        plugin.indexAll(SOURCE);

        TurSNJobItem c1 = itemById(context, "c1");
        assertEquals("Sudeste", c1.getAttributes().get("region"), "deterministic enricher still runs");
        assertFalse(c1.getAttributes().containsKey("area"),
                "with LLM disabled the classified field is omitted, never fabricated");
        assertEquals(0, calls.get(), "disabled classifier is never called");
    }

    @Test
    void unmappedDerivationOmitsTheFieldGroundingSafe() {
        CapturingContext context = new CapturingContext();
        Plugin plugin = new Plugin(context, chain(true, new AtomicInteger()));

        plugin.indexAll(SOURCE);

        // c3 has uf "XX" (not in the region table) -> region omitted, not blank/placeholder.
        TurSNJobItem c3 = itemById(context, "c3");
        assertFalse(c3.getAttributes().containsKey("region"),
                "an unmapped derivation omits the field rather than inventing one");
    }

    private static DumEnrichmentChain chain(boolean llmEnabled, AtomicInteger calls) {
        DumNormalizationEnricher region = DumNormalizationEnricher.lookup("region", "uf", "region",
                Map.of("SP", "Sudeste", "BA", "Nordeste"));

        DumClassifier classifier = input -> {
            calls.incrementAndGet();
            return input.toLowerCase(Locale.ROOT).contains("calculus")
                    ? Optional.of("Exact Sciences")
                    : Optional.empty();
        };
        DumClassificationEnricher area = DumClassificationEnricher.builder("area-llm")
                .enabled(llmEnabled)
                .targetField("area")
                .inputField("description")
                .classifier(classifier)
                .cache(new DumEnrichmentCache(0))
                .build();

        return DumEnrichmentChain.of(region, area);
    }

    private static TurSNJobItem itemById(CapturingContext context, String id) {
        return context.items.stream()
                .map(DumJobItemWithSession::turSNJobItem)
                .filter(item -> id.equals(item.getAttributes().get(TurSNFieldName.ID)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No job item with id " + id));
    }

    // --- Sample structured source -------------------------------------------------------------

    private static final class Catalog implements DumStructuredSource {
        private final DumStructuredSchema schema = DumStructuredSchema.builder()
                .text("title")
                .text("description")
                .facet("area")
                .string("uf")
                .string("region")
                .build();

        @Override
        public String getSourceName() {
            return SOURCE;
        }

        @Override
        public String getProviderName() {
            return "STRUCTURED-ENRICH";
        }

        @Override
        public Collection<String> getSiteNames() {
            return List.of("Sample");
        }

        @Override
        public DumStructuredSchema schema() {
            return schema;
        }

        @Override
        public List<String> discover() {
            return List.of("c1", "c2", "c3");
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            return switch (contentId) {
                // Thin description, no area declared -> LLM classifies; uf maps to a region.
                case "c1" -> Optional.of(DumStructuredRecord.builder("c1", schema)
                        .set("title", "Calculus I")
                        .set("description", "Intro calculus")
                        .set("uf", "SP")
                        .build());
                // Source already declares area -> must survive the classifier.
                case "c2" -> Optional.of(DumStructuredRecord.builder("c2", schema)
                        .set("title", "History")
                        .set("description", "World history survey")
                        .set("area", "Humanities")
                        .set("uf", "BA")
                        .build());
                // Unknown uf -> region cannot be derived.
                case "c3" -> Optional.of(DumStructuredRecord.builder("c3", schema)
                        .set("title", "Misc")
                        .set("description", "Something")
                        .set("uf", "XX")
                        .build());
                default -> Optional.empty();
            };
        }
    }

    private static final class Provider implements DumStructuredSourceProvider {
        private final Catalog catalog = new Catalog();

        @Override
        public String getProviderName() {
            return "STRUCTURED-ENRICH";
        }

        @Override
        public Collection<String> sourceNames() {
            return List.of(SOURCE);
        }

        @Override
        public Optional<DumStructuredSource> resolve(String sourceName) {
            return SOURCE.equals(sourceName) ? Optional.of(catalog) : Optional.empty();
        }
    }

    private static final class Plugin extends DumStructuredSourcePlugin {
        private final DumStructuredSourceProvider provider = new Provider();

        Plugin(DumConnectorContext context, DumEnrichmentChain chain) {
            super(context, chain);
        }

        @Override
        protected DumStructuredSourceProvider provider() {
            return provider;
        }
    }

    private static final class CapturingContext implements DumConnectorContext {
        private final List<DumJobItemWithSession> items = new ArrayList<>();

        @Override
        public boolean addJobItem(DumJobItemWithSession dumJobItemWithSession) {
            return items.add(dumJobItemWithSession);
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            // no-op
        }

        @Override
        public List<DumConnectorIndexing> getIndexingItem(String objectId, String source,
                String provider) {
            return List.of();
        }

        @Override
        public List<String> getObjectIdByDependency(String source, String provider,
                List<String> dependenciesObjectIdList) {
            return List.of();
        }
    }
}
