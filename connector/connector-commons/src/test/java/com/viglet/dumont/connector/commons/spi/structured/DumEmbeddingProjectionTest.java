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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.sn.field.TurSNFieldName;

/**
 * Roadmap <strong>D17</strong>: fields that declare an {@link DumEmbeddingRole} are projected onto
 * the canonical keys ({@code title}/{@code abstract}/{@code text}) that Turing's RAG partitioner
 * reads, so a source whose semantic content lives under custom field names still embeds well — while
 * the original fields are kept and the grounding contract holds (no fabrication, source wins).
 */
class DumEmbeddingProjectionTest {

    private static final String SOURCE = "catalog";

    @Test
    void projectsCustomNamedFieldsOntoCanonicalEmbeddingKeys() {
        TurSNJobItem item = indexAndGet("c1");

        // Custom-named fields are projected onto the keys the partitioner reads...
        assertEquals("Algorithms", item.getAttributes().get(TurSNFieldName.TITLE));
        assertEquals("Sorting and complexity", item.getAttributes().get(TurSNFieldName.TEXT));
        // ...while the original fields are kept for display/facets (nothing renamed).
        assertEquals("Algorithms", item.getAttributes().get("course_name"));
        assertEquals("Sorting and complexity", item.getAttributes().get("ementa"));
    }

    @Test
    void doesNotProjectWhenTheRoleFieldIsAbsent() {
        // c2 has no "ementa" -> no body to project; title still projects.
        TurSNJobItem item = indexAndGet("c2");

        assertEquals("Calculus", item.getAttributes().get(TurSNFieldName.TITLE));
        assertFalse(item.getAttributes().containsKey(TurSNFieldName.TEXT),
                "an absent role field must not synthesize a canonical key");
    }

    @Test
    void leavesFieldsWithoutAnEmbeddingRoleUntouched() {
        TurSNJobItem item = indexAndGet("c1");

        // "area" carries no embedding role -> it is not projected onto any canonical key.
        assertEquals(List.of("Computer Science"), item.getAttributes().get("area"));
        assertFalse(item.getAttributes().containsKey(TurSNFieldName.ABSTRACT));
    }

    private TurSNJobItem indexAndGet(String id) {
        CapturingContext context = new CapturingContext();
        DumStructuredSourcePlugin plugin = new CatalogPlugin(context);
        plugin.indexById(SOURCE, List.of(id));
        return context.items.stream()
                .map(DumJobItemWithSession::turSNJobItem)
                .filter(item -> id.equals(item.getAttributes().get(TurSNFieldName.ID)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No job item with id " + id));
    }

    // --- Sample source whose meaningful content lives under non-standard field names -------------

    private static final class Catalog implements DumStructuredSource {
        private final DumStructuredSchema schema = DumStructuredSchema.builder()
                .field(DumStructuredField.builder("course_name")
                        .embeddingRole(DumEmbeddingRole.TITLE).build())
                .body("ementa")
                .facet("area")
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
            return List.of("c1", "c2");
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            return switch (contentId) {
                case "c1" -> Optional.of(DumStructuredRecord.builder("c1", schema)
                        .set("course_name", "Algorithms")
                        .set("ementa", "Sorting and complexity")
                        .set("area", "Computer Science")
                        .build());
                case "c2" -> Optional.of(DumStructuredRecord.builder("c2", schema)
                        .set("course_name", "Calculus")
                        .set("area", "Mathematics")
                        .build());
                default -> Optional.empty();
            };
        }
    }

    private static final class CatalogProvider implements DumStructuredSourceProvider {
        private final Catalog catalog = new Catalog();

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

    private static final class CatalogPlugin extends DumStructuredSourcePlugin {
        private final DumStructuredSourceProvider provider = new CatalogProvider();

        CatalogPlugin(DumConnectorContext context) {
            super(context);
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
            // no-op for this test
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
