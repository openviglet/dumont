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

package com.viglet.dumont.connector.commons.spi.structured.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenance;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * Plugin-level test for D18 (Done criteria): the same entity arriving from two sources indexes as one
 * canonical document with merged, provenance-tagged fields — each field citing the source that won it.
 */
class DumCrossSourceResolutionPluginTest {

    private CapturingContext context;

    @BeforeEach
    void setUp() {
        context = new CapturingContext();
    }

    @Test
    void sameEntityFromTwoSourcesIndexesAsOneMergedCanonicalDocument() {
        // catalog-a knows the title; catalog-b (a mirror, code in a different case) knows the workload.
        FixtureSource catalogA = new FixtureSource("catalog-a", List.of("Sample-A"), Map.of(
                "a-1", fixture("CS101", "Algorithms", null)));
        FixtureSource catalogB = new FixtureSource("catalog-b", List.of("Sample-B"), Map.of(
                "b-7", fixture("cs101", null, "60h")));
        CatalogPlugin plugin = new CatalogPlugin(context, DumCanonicalKey.field("code"),
                catalogA, catalogB);

        plugin.crawl();

        assertEquals(1, context.items.size(),
                "the two source records collapse to a single canonical document");
        TurSNJobItem item = context.items.get(0).turSNJobItem();
        Map<String, Object> attributes = item.getAttributes();

        // Merged values: title from catalog-a, workload from catalog-b, code from the first contributor.
        assertEquals("Algorithms", attributes.get("title"));
        assertEquals("60h", attributes.get("workload"));
        assertEquals("CS101", attributes.get("code"));

        @SuppressWarnings("unchecked")
        List<String> tokens = (List<String>) attributes.get(DumProvenance.FIELD);
        assertTrue(tokens.contains("title|catalog-a|css|h1"),
                "the title field cites the source that supplied it");
        assertTrue(tokens.contains("workload|catalog-b|css|.workload"),
                "the workload field cites the other source that supplied it");

        // The canonical document is published under its primary source's sites/locale.
        assertEquals(List.of("Sample-A"), item.getSiteNames());
    }

    @Test
    void firstContributorWinsAndIsNeverOverwritten() {
        // Both mirrors carry a title; the first to contribute keeps it, the later one does not replace it.
        FixtureSource catalogA = new FixtureSource("catalog-a", List.of("Sample-A"), Map.of(
                "a-1", fixture("CS101", "Algorithms", null)));
        FixtureSource catalogB = new FixtureSource("catalog-b", List.of("Sample-B"), Map.of(
                "b-7", fixture("CS101", "Intro to Algorithms", "60h")));
        CatalogPlugin plugin = new CatalogPlugin(context, DumCanonicalKey.field("code"),
                catalogA, catalogB);

        plugin.crawl();

        assertEquals(1, context.items.size());
        Map<String, Object> attributes = context.items.get(0).turSNJobItem().getAttributes();
        assertEquals("Algorithms", attributes.get("title"), "the first contributor's title is kept");
        assertEquals("60h", attributes.get("workload"), "the gap is still filled by the mirror");
    }

    @Test
    void recordWithoutCanonicalKeyIsPublishedUnmerged() {
        FixtureSource catalog = new FixtureSource("catalog-a", List.of("Sample-A"), Map.of(
                "a-1", fixture(null, "Orphan course", "40h")));
        CatalogPlugin plugin = new CatalogPlugin(context, DumCanonicalKey.field("code"), catalog);

        plugin.crawl();

        assertEquals(1, context.items.size(), "an unkeyed record still indexes, on its own id");
        assertEquals("a-1", context.items.get(0).turSNJobItem().getAttributes().get("id"));
    }

    // --- Fixtures --------------------------------------------------------------------------------

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .field(DumStructuredField.builder("code").type(TurSEFieldType.STRING).build())
            .field(DumStructuredField.builder("title").type(TurSEFieldType.TEXT).mandatory(true).build())
            .text("workload")
            .build();

    /** A small record spec: code (canonical key), title, workload — any may be absent (null). */
    private record Fixture(String code, String title, String workload) {
    }

    private static Fixture fixture(String code, String title, String workload) {
        return new Fixture(code, title, workload);
    }

    private static final class FixtureSource implements DumStructuredSource {
        private final String name;
        private final Collection<String> sites;
        private final Map<String, Fixture> fixtures;

        FixtureSource(String name, Collection<String> sites, Map<String, Fixture> fixtures) {
            this.name = name;
            this.sites = sites;
            this.fixtures = new LinkedHashMap<>(fixtures);
        }

        @Override
        public String getSourceName() {
            return name;
        }

        @Override
        public String getProviderName() {
            return "STRUCTURED-TEST";
        }

        @Override
        public Collection<String> getSiteNames() {
            return sites;
        }

        @Override
        public DumStructuredSchema schema() {
            return SCHEMA;
        }

        @Override
        public Locale getLocale() {
            return Locale.US;
        }

        @Override
        public List<String> discover() {
            return List.copyOf(fixtures.keySet());
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            Fixture fixture = fixtures.get(contentId);
            if (fixture == null) {
                return Optional.empty();
            }
            DumStructuredRecord.Builder record = DumStructuredRecord.builder(contentId, SCHEMA);
            record.set("code", fixture.code(), DumFieldProvenance.css(".code"));
            record.set("title", fixture.title(), DumFieldProvenance.css("h1"));
            record.set("workload", fixture.workload(), DumFieldProvenance.css(".workload"));
            return Optional.of(record.build());
        }
    }

    private static final class CatalogPlugin extends DumStructuredSourcePlugin
            implements DumStructuredSourceProvider {
        private final Map<String, DumStructuredSource> sources = new LinkedHashMap<>();

        CatalogPlugin(DumConnectorContext context, DumCanonicalKey canonicalKey,
                FixtureSource... sources) {
            super(context, DumEnrichmentChain.empty(), null, null, null, canonicalKey);
            for (FixtureSource source : sources) {
                this.sources.put(source.getSourceName(), source);
            }
        }

        @Override
        protected DumStructuredSourceProvider provider() {
            return this;
        }

        @Override
        public String getProviderName() {
            return "STRUCTURED-TEST";
        }

        @Override
        public Collection<String> sourceNames() {
            return sources.keySet();
        }

        @Override
        public Optional<DumStructuredSource> resolve(String sourceName) {
            return Optional.ofNullable(sources.get(sourceName));
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
