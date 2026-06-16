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

package com.viglet.dumont.connector.commons.spi.structured.coverage;

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
import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * Plugin-level test for D15 (Done criterion): a fixture whose layout changes between two full index
 * runs raises a drift flag naming the affected field, and the coverage history is queryable per
 * source across runs.
 */
class DumStructuredCoveragePluginTest {

    private static final String SOURCE = "courses";

    private CapturingContext context;
    private DumInMemoryCoverageHistoryStore coverageStore;
    private CatalogPlugin plugin;

    @BeforeEach
    void setUp() {
        context = new CapturingContext();
        coverageStore = new DumInMemoryCoverageHistoryStore();
        plugin = new CatalogPlugin(context, coverageStore);
    }

    @Test
    void layoutBreakBetweenTwoRunsRaisesDriftNamingTheField() {
        // First run: both fields extract cleanly.
        plugin.indexAll(SOURCE);
        // The detail-page layout changes — the "workload" selector no longer matches, so the field
        // stops being extracted on the next run, while the source still discovers the same ids.
        plugin.catalog.breakWorkloadSelector();
        plugin.indexAll(SOURCE);

        List<DumCoverageSnapshot> history = coverageStore.history(SOURCE);
        assertEquals(2, history.size(), "coverage history is queryable per source across runs");

        DumCoverageSnapshot current = history.get(0);
        DumCoverageSnapshot previous = history.get(1);
        assertEquals(100, previous.field("workload").orElseThrow().percent());
        assertEquals(0, current.field("workload").orElseThrow().percent());

        DumCoverageDrift drift = DumCoverageMonitor.drift(previous, current,
                DumCoverageMonitor.DEFAULT_DROP_THRESHOLD);
        assertTrue(drift.hasDrift(), "a sharp coverage drop must raise a drift flag");
        assertEquals(List.of("workload"), drift.driftedFields(),
                "the drift flag must name the affected field");
        // The mandatory title field kept indexing, so it must not be flagged.
        assertFalse(drift.driftedFields().contains("title"));
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
                    .set("title", title);
            if (workloadExtractable) {
                record.set("workload", "60h");
            }
            return Optional.of(record.build());
        }
    }

    private static final class CatalogPlugin extends DumStructuredSourcePlugin
            implements DumStructuredSourceProvider {
        private final MutableCatalog catalog = new MutableCatalog();

        CatalogPlugin(DumConnectorContext context, DumCoverageHistoryStore coverageStore) {
            super(context, DumEnrichmentChain.empty(), new DumInMemoryPreviewSnapshotStore(),
                    coverageStore);
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
