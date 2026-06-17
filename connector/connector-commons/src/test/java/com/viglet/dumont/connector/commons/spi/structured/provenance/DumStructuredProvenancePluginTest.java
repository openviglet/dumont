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

package com.viglet.dumont.connector.commons.spi.structured.provenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourcePlugin;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnrichmentChain;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumInMemoryPreviewSnapshotStore;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenanceLastSeen.FieldLastSeen;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * Plugin-level test for D16 (Done criteria): a published document carries per-field provenance, and a
 * field dropped because the source stopped declaring it is visible as last-seen at an earlier run.
 */
class DumStructuredProvenancePluginTest {

    private static final String SOURCE = "courses";

    private CapturingContext context;
    private DumInMemoryProvenanceHistoryStore provenanceStore;
    private CatalogPlugin plugin;

    @BeforeEach
    void setUp() {
        context = new CapturingContext();
        provenanceStore = new DumInMemoryProvenanceHistoryStore();
        plugin = new CatalogPlugin(context, provenanceStore);
    }

    @Test
    void publishedDocumentCarriesCompactPerFieldProvenance() {
        plugin.indexAll(SOURCE);

        assertEquals(2, context.items.size());
        TurSNJobItem item = context.items.get(0).turSNJobItem();

        @SuppressWarnings("unchecked")
        List<String> tokens = (List<String>) item.getAttributes().get(DumProvenance.FIELD);
        assertTrue(tokens.contains("title|courses|css|h1.title"),
                "the title field must cite the source, strategy and selector it came from");
        assertTrue(tokens.contains("workload|courses|css|.workload"));
        // The reserved provenance field travels as a declared spec alongside the document.
        assertTrue(item.getSpecs().stream().anyMatch(spec -> DumProvenance.FIELD.equals(spec.getName())));
    }

    @Test
    void recordCarriesProvenanceOnlyForPresentFields() {
        DumStructuredRecord record = plugin.catalog.extract("c1").orElseThrow();
        assertEquals(record.values().keySet(), record.provenance().keySet(),
                "provenance is a faithful subset of the grounded values");
        assertEquals("h1.title", record.provenance().get("title").selector());
        assertEquals("css", record.provenance().get("title").strategy());
    }

    @Test
    void droppedFieldIsVisibleAsLastSeenAtAnEarlierRun() {
        // First run: both fields extract cleanly.
        plugin.indexAll(SOURCE);
        // The detail-page layout changes — the "workload" selector no longer matches on the next run.
        plugin.catalog.breakWorkloadSelector();
        plugin.indexAll(SOURCE);

        List<DumProvenanceSnapshot> history = provenanceStore.history(SOURCE);
        assertEquals(2, history.size(), "provenance history is queryable per source across runs");

        DumProvenanceLastSeen lastSeen = DumProvenanceMonitor.lastSeen(history);
        FieldLastSeen workload = field(lastSeen, "workload");
        FieldLastSeen title = field(lastSeen, "title");

        assertFalse(workload.presentInLatest(), "workload stopped being declared in the latest run");
        assertEquals(1, workload.lastSeenRunsAgo(), "workload was last seen one run ago");
        assertEquals("css", workload.strategy(), "the origin it used to come from is preserved");
        assertEquals(".workload", workload.selector());
        assertEquals(List.of("workload"), lastSeen.droppedFields());

        assertTrue(title.presentInLatest(), "the mandatory title field kept indexing");
        assertEquals(0, title.lastSeenRunsAgo());
    }

    private static FieldLastSeen field(DumProvenanceLastSeen lastSeen, String name) {
        return lastSeen.fields().stream().filter(field -> field.field().equals(name)).findFirst()
                .orElseThrow();
    }

    // --- A small, mutable in-memory catalog whose detail layout can "break" ----------------------

    private static final class MutableCatalog implements DumStructuredSource {
        private final DumStructuredSchema schema = DumStructuredSchema.builder()
                .field(DumStructuredField.builder("title").type(TurSEFieldType.TEXT).mandatory(true)
                        .build())
                .text("workload")
                .build();
        private final Map<String, String> titles = new LinkedHashMap<>(Map.of(
                "c1", "Algorithms", "c2", "Calculus"));
        private boolean workloadExtractable = true;

        void breakWorkloadSelector() {
            workloadExtractable = false;
        }

        @Override
        public String getSourceName() {
            return SOURCE;
        }

        @Override
        public String getProviderName() {
            return "STRUCTURED-TEST";
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
            return List.copyOf(titles.keySet());
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            String title = titles.get(contentId);
            if (title == null) {
                return Optional.empty();
            }
            DumStructuredRecord.Builder record = DumStructuredRecord.builder(contentId, schema)
                    .set("title", title, DumFieldProvenance.css("h1.title"));
            if (workloadExtractable) {
                record.set("workload", "60h", DumFieldProvenance.css(".workload"));
            }
            return Optional.of(record.build());
        }
    }

    private static final class CatalogPlugin extends DumStructuredSourcePlugin
            implements DumStructuredSourceProvider {
        private final MutableCatalog catalog = new MutableCatalog();

        CatalogPlugin(DumConnectorContext context, DumProvenanceHistoryStore provenanceStore) {
            super(context, DumEnrichmentChain.empty(), new DumInMemoryPreviewSnapshotStore(), null,
                    provenanceStore);
        }

        @Override
        protected DumStructuredSourceProvider provider() {
            return this;
        }

        @Override
        public String getProviderName() {
            return catalog.getProviderName();
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

    private static final class CapturingContext implements DumConnectorContext {
        private final List<DumJobItemWithSession> items = new ArrayList<>();

        @Override
        public boolean addJobItem(DumJobItemWithSession dumJobItemWithSession) {
            return items.add(dumJobItemWithSession);
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            // no-op for the test
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
