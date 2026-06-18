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

package com.viglet.dumont.connector.commons.spi.structured;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.field.TurSNFieldName;

/**
 * End-to-end test for the structured-source SPI (roadmap D01): a sample tabular catalog indexes
 * through {@link DumStructuredSourcePlugin} with a declared field set, and the grounding contract
 * holds (absent fields omitted, mandatory-missing records skipped).
 */
class DumStructuredSourcePluginTest {

    private static final String SOURCE = "courses";

    private CapturingContext context;
    private DumStructuredSourcePlugin plugin;

    @BeforeEach
    void setUp() {
        context = new CapturingContext();
        plugin = new SampleCatalogPlugin(context, new SampleCatalogProvider());
    }

    @Test
    void indexesDeclaredFieldsThroughTheSpi() {
        plugin.indexAll(SOURCE);

        // c3 is missing the mandatory "title" -> skipped, never fabricated.
        assertEquals(2, context.items.size());
        assertEquals(1, context.finishedSessions.size());
        assertFalse(context.standalone, "indexAll publishes a non-standalone session");

        TurSNJobItem c1 = itemById("c1");
        assertEquals("Algorithms", c1.getAttributes().get("title"));
        assertEquals("c1", c1.getAttributes().get(TurSNFieldName.ID));
        assertEquals(List.of("Computer Science", "Mathematics"), c1.getAttributes().get("area"));
        assertEquals("Sorting and complexity", c1.getAttributes().get("description"));
        // Declared schema travels with the document.
        assertEquals(4, c1.getSpecs().size());
    }

    @Test
    void omitsAbsentFieldsInsteadOfPublishingEmptyValues() {
        plugin.indexAll(SOURCE);

        TurSNJobItem c2 = itemById("c2");
        // Optional "description" was blank at the source -> absent, not "".
        assertFalse(c2.getAttributes().containsKey("description"),
                "absent field must be omitted, not sent as empty string");
        assertEquals("Calculus", c2.getAttributes().get("title"));
        assertEquals(List.of("Mathematics"), c2.getAttributes().get("area"));
    }

    @Test
    void interruptedIndexAllFinishesStandaloneSoPriorIndexSurvives() {
        // Roadmap D33: an indexAll interrupted partway must finish *standalone* so the destructive
        // deindex sweep is skipped — a transient interruption cannot wipe the still-valid index.
        InterruptingCatalogProvider provider = new InterruptingCatalogProvider();
        DumStructuredSourcePlugin interruptingPlugin = new SampleCatalogPlugin(context, provider);
        try {
            interruptingPlugin.indexAll("interrupting");

            assertTrue(context.standalone,
                    "an interrupted indexAll finishes standalone (no destructive deindex)");
            assertEquals(1, context.finishedSessions.size(), "the session is still finished");
            assertEquals(1, context.items.size(), "only the records extracted before the interrupt");
        } finally {
            Thread.interrupted(); // clear the flag so it cannot leak into another test
        }
    }

    @Test
    void abortingIndexAllFinishesStandaloneSoPriorIndexSurvives() {
        // Roadmap D33: an exception escaping the extraction loop (here from the publish path) must
        // also finish standalone rather than leave the session dangling or drive a partial deindex.
        ThrowingContext throwingContext = new ThrowingContext();
        DumStructuredSourcePlugin abortingPlugin =
                new SampleCatalogPlugin(throwingContext, new AbortingCatalogProvider());

        abortingPlugin.indexAll("aborting");

        assertTrue(throwingContext.standalone, "an aborted indexAll finishes standalone");
        assertEquals(1, throwingContext.finishedSessions.size(), "the session is still finished");
    }

    @Test
    void indexByIdPublishesStandaloneSession() {
        plugin.indexById(SOURCE, List.of("c1"));

        assertEquals(1, context.items.size());
        assertTrue(context.standalone, "indexById publishes a standalone session");
    }

    @Test
    void discoverDelegatesToTheSource() {
        assertEquals(List.of("c1", "c2", "c3"), plugin.discoverContentIds(SOURCE));
        assertTrue(plugin.ownsSource(SOURCE));
        assertFalse(plugin.ownsSource("unknown"));
        assertEquals("STRUCTURED-SAMPLE", plugin.getProviderName());
    }

    @Test
    void recordRejectsUndeclaredFieldsAndBadTypes() {
        DumStructuredSchema schema = DumStructuredSchema.builder()
                .field(DumStructuredField.builder("title").type(TurSEFieldType.TEXT)
                        .mandatory(true).build())
                .field(DumStructuredField.builder("credits").type(TurSEFieldType.INT).build())
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> DumStructuredRecord.builder("x", schema).set("ghost", "v"));
        assertThrows(IllegalArgumentException.class,
                () -> DumStructuredRecord.builder("x", schema).set("credits", "not-a-number"));

        // Blank values are grounded away; the field stays absent.
        DumStructuredRecord record = DumStructuredRecord.builder("x", schema)
                .set("title", "  ")
                .build();
        assertFalse(record.has("title"));
        assertEquals(List.of("title"), record.missingMandatoryFields());
    }

    private TurSNJobItem itemById(String id) {
        return context.items.stream()
                .map(DumJobItemWithSession::turSNJobItem)
                .filter(item -> id.equals(item.getAttributes().get(TurSNFieldName.ID)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No job item with id " + id));
    }

    // --- Sample structured source (a tabular course catalog) ---------------------------------

    private static final class SampleCatalog implements DumStructuredSource {
        private final DumStructuredSchema schema = DumStructuredSchema.builder()
                .field(DumStructuredField.builder("title").type(TurSEFieldType.TEXT)
                        .mandatory(true).build())
                .facet("area")
                .text("description")
                .field(DumStructuredField.builder("credits").type(TurSEFieldType.INT).build())
                .build();

        @Override
        public String getSourceName() {
            return SOURCE;
        }

        @Override
        public String getProviderName() {
            return "STRUCTURED-SAMPLE";
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
        public Locale getLocale() {
            return Locale.US;
        }

        @Override
        public List<String> discover() {
            return List.of("c1", "c2", "c3");
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            return switch (contentId) {
                case "c1" -> Optional.of(DumStructuredRecord.builder("c1", schema)
                        .set("title", "Algorithms")
                        .set("area", List.of("Computer Science", "Mathematics"))
                        .set("description", "Sorting and complexity")
                        .set("credits", 4)
                        .build());
                case "c2" -> Optional.of(DumStructuredRecord.builder("c2", schema)
                        .set("title", "Calculus")
                        .set("area", "Mathematics")
                        .set("description", "") // blank -> grounded away
                        .set("credits", 6)
                        .build());
                // c3: missing the mandatory "title" -> the pipeline must skip it.
                case "c3" -> Optional.of(DumStructuredRecord.builder("c3", schema)
                        .set("area", "Physics")
                        .build());
                default -> Optional.empty();
            };
        }
    }

    private static final class SampleCatalogProvider implements DumStructuredSourceProvider {
        private final SampleCatalog catalog = new SampleCatalog();

        @Override
        public String getProviderName() {
            return "STRUCTURED-SAMPLE";
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

    // --- Sources that model an interrupted / aborted run (roadmap D33) ------------------------

    /** A source that interrupts the calling thread after its first extraction. */
    private static final class InterruptingCatalog implements DumStructuredSource {
        private final DumStructuredSchema schema = DumStructuredSchema.builder()
                .field(DumStructuredField.builder("title").type(TurSEFieldType.TEXT)
                        .mandatory(true).build())
                .build();

        @Override
        public String getSourceName() {
            return "interrupting";
        }

        @Override
        public String getProviderName() {
            return "STRUCTURED-SAMPLE";
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
        public Locale getLocale() {
            return Locale.US;
        }

        @Override
        public List<String> discover() {
            return List.of("c1", "c2", "c3");
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            DumStructuredRecord record = DumStructuredRecord.builder(contentId, schema)
                    .set("title", "Title " + contentId)
                    .build();
            if ("c1".equals(contentId)) {
                // Simulate a pool/JVM shutdown landing on the extraction thread mid-run.
                Thread.currentThread().interrupt();
            }
            return Optional.of(record);
        }
    }

    /** A source whose extraction throws, modelling an exception escaping the per-source loop. */
    private static final class AbortingCatalog implements DumStructuredSource {
        private final DumStructuredSchema schema = DumStructuredSchema.builder()
                .field(DumStructuredField.builder("title").type(TurSEFieldType.TEXT)
                        .mandatory(true).build())
                .build();

        @Override
        public String getSourceName() {
            return "aborting";
        }

        @Override
        public String getProviderName() {
            return "STRUCTURED-SAMPLE";
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
        public Locale getLocale() {
            return Locale.US;
        }

        @Override
        public List<String> discover() {
            return List.of("c1");
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            // extractOne swallows extract() failures, so the abort is injected on the publish path
            // via ThrowingContext.addJobItem — modelling an exception escaping the per-source loop.
            return Optional.of(DumStructuredRecord.builder(contentId, schema)
                    .set("title", "Boom " + contentId)
                    .build());
        }
    }

    private static final class InterruptingCatalogProvider implements DumStructuredSourceProvider {
        private final InterruptingCatalog catalog = new InterruptingCatalog();

        @Override
        public String getProviderName() {
            return "STRUCTURED-SAMPLE";
        }

        @Override
        public Collection<String> sourceNames() {
            return List.of("interrupting");
        }

        @Override
        public Optional<DumStructuredSource> resolve(String sourceName) {
            return "interrupting".equals(sourceName) ? Optional.of(catalog) : Optional.empty();
        }
    }

    private static final class AbortingCatalogProvider implements DumStructuredSourceProvider {
        private final AbortingCatalog catalog = new AbortingCatalog();

        @Override
        public String getProviderName() {
            return "STRUCTURED-SAMPLE";
        }

        @Override
        public Collection<String> sourceNames() {
            return List.of("aborting");
        }

        @Override
        public Optional<DumStructuredSource> resolve(String sourceName) {
            return "aborting".equals(sourceName) ? Optional.of(catalog) : Optional.empty();
        }
    }

    private static final class SampleCatalogPlugin extends DumStructuredSourcePlugin {
        private final DumStructuredSourceProvider provider;

        SampleCatalogPlugin(DumConnectorContext context, DumStructuredSourceProvider provider) {
            super(context);
            this.provider = provider;
        }

        @Override
        protected DumStructuredSourceProvider provider() {
            return provider;
        }
    }

    // --- Capturing context (the indexing seam shared with the web crawler) --------------------

    private static final class CapturingContext implements DumConnectorContext {
        private final List<DumJobItemWithSession> items = new ArrayList<>();
        private final List<DumConnectorSession> finishedSessions = new ArrayList<>();
        private boolean standalone;

        @Override
        public boolean addJobItem(DumJobItemWithSession dumJobItemWithSession) {
            return items.add(dumJobItemWithSession);
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            this.finishedSessions.add(session);
            this.standalone = standalone;
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

    /** A context whose publish path throws, modelling an exception escaping the per-source loop. */
    private static final class ThrowingContext implements DumConnectorContext {
        private final List<DumConnectorSession> finishedSessions = new ArrayList<>();
        private boolean standalone;

        @Override
        public boolean addJobItem(DumJobItemWithSession dumJobItemWithSession) {
            throw new IllegalStateException("boom on publish");
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            this.finishedSessions.add(session);
            this.standalone = standalone;
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
