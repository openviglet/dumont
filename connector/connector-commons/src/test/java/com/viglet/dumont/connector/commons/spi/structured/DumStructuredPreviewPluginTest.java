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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
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
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnrichmentChain;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumInMemoryPreviewSnapshotStore;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumPreviewSnapshotStore;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumStructuredPreview;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * Plugin-level test for the D14 dry-run preview: a preview writes nothing (no job items, no finished
 * session), and a real {@code indexAll} captures the baseline that a later preview diffs against —
 * the same source previewed right after indexing reports every sample as unchanged.
 */
class DumStructuredPreviewPluginTest {

    private static final String SOURCE = "courses";

    private CapturingContext context;
    private DumPreviewSnapshotStore store;
    private MutableCatalogPlugin plugin;

    @BeforeEach
    void setUp() {
        context = new CapturingContext();
        store = new DumInMemoryPreviewSnapshotStore();
        plugin = new MutableCatalogPlugin(context, store);
    }

    @Test
    void previewWritesNothingAndReportsCoverageAndDiff() {
        DumStructuredPreview preview = plugin.preview(SOURCE, 20);

        // Done criterion: a preview run writes nothing.
        assertTrue(context.items.isEmpty(), "preview must not enqueue any job item");
        assertTrue(context.finishedSessions.isEmpty(), "preview must not finish an indexing session");
        assertTrue(store.load(SOURCE).isEmpty(), "preview must not persist a baseline");

        // ...and reports per-field coverage plus an add/change/remove count for the sample.
        assertEquals(2, preview.discovered());
        assertFalse(preview.baseline());
        assertEquals(2, preview.documentDiff().added());
        assertEquals(2, preview.coverage().size());
    }

    @Test
    void indexAllCapturesBaselineThenPreviewSeesNoChange() {
        plugin.indexAll(SOURCE);
        assertEquals(2, context.items.size());
        assertTrue(store.load(SOURCE).isPresent(), "indexAll captures a baseline snapshot");

        DumStructuredPreview preview = plugin.preview(SOURCE, 20);
        assertTrue(preview.baseline());
        assertEquals(0, preview.documentDiff().added());
        assertEquals(0, preview.documentDiff().changed());
        assertEquals(2, preview.documentDiff().unchanged());
    }

    @Test
    void previewAfterEditDetectsTheChange() {
        plugin.indexAll(SOURCE);
        plugin.catalog.setTitle("c1", "Algorithms II"); // re-config / source edit

        DumStructuredPreview preview = plugin.preview(SOURCE, 20);
        assertEquals(1, preview.documentDiff().changed());
        assertEquals(1, preview.documentDiff().unchanged());
    }

    // --- A small, mutable in-memory catalog -----------------------------------------------------

    private static final class MutableCatalog implements DumStructuredSource {
        private final DumStructuredSchema schema = DumStructuredSchema.builder()
                .field(DumStructuredField.builder("title").type(TurSEFieldType.TEXT).mandatory(true)
                        .build())
                .text("description")
                .build();
        private final Map<String, String> titles = new java.util.LinkedHashMap<>(Map.of(
                "c1", "Algorithms", "c2", "Calculus"));

        void setTitle(String id, String title) {
            titles.put(id, title);
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
            return Optional.of(DumStructuredRecord.builder(contentId, schema).set("title", title).build());
        }
    }

    private static final class MutableCatalogPlugin extends DumStructuredSourcePlugin
            implements DumStructuredSourceProvider {
        private final MutableCatalog catalog = new MutableCatalog();

        MutableCatalogPlugin(DumConnectorContext context, DumPreviewSnapshotStore store) {
            super(context, DumEnrichmentChain.empty(), store);
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
        private final List<DumConnectorSession> finishedSessions = new ArrayList<>();

        @Override
        public boolean addJobItem(DumJobItemWithSession dumJobItemWithSession) {
            return items.add(dumJobItemWithSession);
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            finishedSessions.add(session);
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
