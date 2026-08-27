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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumIndexingSchema;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/** The schema reaches the backend ahead of the documents (roadmap D63). */
@DisplayName("Schema provisioning Tests (D63)")
class DumSchemaProvisioningPluginTest {

    private static final String SOURCE = "courses";

    private RecordingContext context;
    private CatalogPlugin plugin;

    @BeforeEach
    void setUp() {
        context = new RecordingContext();
        plugin = new CatalogPlugin(context);
    }

    @Test
    @DisplayName("the declared schema is sent once per site, before any document")
    void schemaIsSentBeforeTheFirstDocument() {
        plugin.indexAll(SOURCE);

        assertEquals(2, context.provisioned.size(), "one declaration per site published to");
        assertEquals(List.of("Author", "Publish"),
                context.provisioned.stream().map(DumIndexingSchema::siteName).toList());
        assertTrue(context.provisionedBeforeAnyDocument,
                "the schema must be declared before the first document is published");
        assertFalse(context.items.isEmpty(), "the run still publishes its documents");
    }

    @Test
    @DisplayName("the declared fields are the specs published with every document, not a copy")
    void declaredFieldsAreTheSameSpecsThePublishUses() {
        plugin.indexAll(SOURCE);

        List<TurSNJobAttributeSpec> declared = context.provisioned.getFirst().fields();
        assertEquals(plugin.catalog.schema().toAttributeSpecs().size(), declared.size());

        TurSNJobAttributeSpec workload = declared.stream()
                .filter(spec -> "workload".equals(spec.getName())).findFirst().orElseThrow();
        // The two properties index-time inference gets wrong when the first value is unremarkable.
        assertTrue(workload.isMultiValued(), "a multi-valued field must be declared multi-valued");
        assertTrue(workload.isFacet(), "a facet must be declared as one");
        assertEquals(Locale.US, context.provisioned.getFirst().locale());
    }

    @Test
    @DisplayName("a per-id update does not re-declare the schema")
    void indexByIdDoesNotProvision() {
        plugin.indexById(SOURCE, List.of("c1"));

        assertTrue(context.provisioned.isEmpty(),
                "a single-document update runs against a site a full run already declared");
        assertFalse(context.items.isEmpty());
    }

    @Test
    @DisplayName("a source publishing to no site declares nothing")
    void aSourceWithNoSiteDeclaresNothing() {
        plugin.catalog.publishToNoSite();

        plugin.indexAll(SOURCE);

        assertTrue(context.provisioned.isEmpty());
    }

    @Test
    @DisplayName("an empty schema is not a declaration")
    void anEmptySchemaIsNotADeclaration() {
        assertTrue(new DumIndexingSchema("Author", Locale.US, List.of()).isEmpty());
        assertFalse(new DumIndexingSchema("Author", Locale.US,
                plugin.catalog.schema().toAttributeSpecs()).isEmpty());
    }

    // --- Fixture --------------------------------------------------------------------------------

    private static final class Catalog implements DumStructuredSource {
        private final DumStructuredSchema schema = DumStructuredSchema.builder()
                .field(DumStructuredField.builder("title").type(TurSEFieldType.TEXT)
                        .mandatory(true).build())
                .field(DumStructuredField.builder("workload").type(TurSEFieldType.STRING)
                        .multiValued(true).facet(true).build())
                .build();
        private final Map<String, String> titles = new LinkedHashMap<>(Map.of(
                "c1", "Algorithms", "c2", "Calculus"));
        private Collection<String> siteNames = List.of("Author", "Publish");

        void publishToNoSite() {
            siteNames = List.of();
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
            return siteNames;
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
            return Optional.of(DumStructuredRecord.builder(contentId, schema)
                    .set("title", title)
                    .set("workload", "60h")
                    .build());
        }
    }

    private static final class CatalogPlugin extends DumStructuredSourcePlugin
            implements DumStructuredSourceProvider {
        private final Catalog catalog = new Catalog();

        CatalogPlugin(DumConnectorContext context) {
            super(context);
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

    private static final class RecordingContext implements DumConnectorContext {
        private final List<DumJobItemWithSession> items = new ArrayList<>();
        private final List<DumIndexingSchema> provisioned = new ArrayList<>();
        private boolean provisionedBeforeAnyDocument = true;

        @Override
        public boolean addJobItem(DumJobItemWithSession dumJobItemWithSession) {
            return items.add(dumJobItemWithSession);
        }

        @Override
        public void provisionSchema(DumIndexingSchema schema) {
            if (!items.isEmpty()) {
                provisionedBeforeAnyDocument = false;
            }
            provisioned.add(schema);
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
