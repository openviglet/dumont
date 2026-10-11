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
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnricher;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnrichmentChain;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumTransient;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance;
import com.viglet.turing.client.sn.job.TurSNJobItem;

/** Working data that travels with a record and never reaches the index (roadmap D97). */
class DumTransientAttributesTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .field(DumStructuredField.builder("title").mandatory(true).build())
            .string("city")
            .build();

    @Test
    void aRecordCarriesTransientsTheSchemaNeverDeclares() {
        DumStructuredRecord rec = DumStructuredRecord.builder("1", SCHEMA)
                .set("title", "Engineer")
                .transientValue("locationRaw", "Recife - PE, Brasil")
                .transientValue("blank", "  ")
                .build();

        assertEquals(Map.of("locationRaw", "Recife - PE, Brasil"), rec.transients());
        assertFalse(rec.values().containsKey("locationRaw"), "a transient is not a field");
        assertThrows(IllegalArgumentException.class,
                () -> DumStructuredRecord.builder("1", SCHEMA).transientValue(" ", "x"));
        assertThrows(IllegalArgumentException.class,
                () -> DumStructuredRecord.builder("1", SCHEMA).set("locationRaw", "x"),
                "set() still refuses an undeclared field");
    }

    @Test
    void toBuilderKeepsEverything() {
        DumStructuredRecord rec = DumStructuredRecord.builder("1", SCHEMA)
                .contentType("job")
                .set("title", "Engineer", DumFieldProvenance.css("h1"))
                .transientValue("locationRaw", "Recife")
                .build();

        DumStructuredRecord copy = rec.toBuilder().transientValue("locationRaw", null).build();

        assertEquals(rec.values(), copy.values());
        assertEquals(rec.provenance(), copy.provenance());
        assertEquals("job", copy.contentType());
        assertTrue(copy.transients().isEmpty(), "a blank value clears the transient");
    }

    @Test
    void enrichersReadAndWriteTransientsThroughTheChain() {
        // The first enricher resolves the raw text; the second sees its note.
        DumEnricher resolver = enricher("resolver", rec -> {
            String raw = (String) rec.transients().get("locationRaw");
            return raw.startsWith("Recife")
                    ? Map.of("city", "Recife", "locationRaw", DumTransient.of(null))
                    : Map.of("locationUnresolved", DumTransient.of(raw));
        });
        List<Map<String, Object>> seen = new ArrayList<>();
        DumEnricher auditor = enricher("auditor", rec -> {
            seen.add(rec.transients());
            return Map.of();
        });
        DumEnrichmentChain chain = DumEnrichmentChain.of(resolver, auditor);

        DumStructuredRecord resolved = chain.apply(null, record("1", "Recife - PE"));
        DumStructuredRecord unresolved = chain.apply(null, record("2", "Atlantis"));

        assertEquals("Recife", resolved.values().get("city"));
        assertTrue(resolved.transients().isEmpty());
        assertEquals("Atlantis", unresolved.transients().get("locationUnresolved"));
        assertEquals("Atlantis", unresolved.transients().get("locationRaw"));
        assertEquals(List.of(Map.of(), Map.of("locationRaw", "Atlantis", "locationUnresolved", "Atlantis")),
                seen);
    }

    @Test
    void thePublishedDocumentCarriesNoTransient() {
        CapturingContext context = new CapturingContext();
        DumStructuredRecord rec = record("1", "Recife - PE");

        new OneRecordPlugin(context, rec).indexAll("jobs");

        TurSNJobItem item = context.items.getFirst().turSNJobItem();
        assertEquals("Engineer", item.getAttributes().get("title"));
        assertFalse(item.getAttributes().containsKey("locationRaw"));
        assertFalse(item.getAttributes().containsValue("Recife - PE"));
    }

    // --- helpers -------------------------------------------------------------------------------

    private static DumStructuredRecord record(String id, String rawLocation) {
        return DumStructuredRecord.builder(id, SCHEMA).set("title", "Engineer")
                .transientValue("locationRaw", rawLocation).build();
    }

    private static DumEnricher enricher(String name,
            java.util.function.Function<DumStructuredRecord, Map<String, Object>> body) {
        return new DumEnricher() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord rec) {
                return body.apply(rec);
            }
        };
    }

    private static final class OneRecordPlugin extends DumStructuredSourcePlugin {
        private final DumStructuredRecord rec;

        OneRecordPlugin(DumConnectorContext context, DumStructuredRecord rec) {
            super(context);
            this.rec = rec;
        }

        @Override
        protected DumStructuredSourceProvider provider() {
            DumStructuredSource source = new DumStructuredSource() {
                @Override
                public String getSourceName() {
                    return "jobs";
                }

                @Override
                public String getProviderName() {
                    return "TEST";
                }

                @Override
                public Collection<String> getSiteNames() {
                    return List.of("site");
                }

                @Override
                public DumStructuredSchema schema() {
                    return SCHEMA;
                }

                @Override
                public List<String> discover() {
                    return List.of(rec.id());
                }

                @Override
                public Optional<DumStructuredRecord> extract(String contentId) {
                    return Optional.of(rec);
                }
            };
            return new DumStructuredSourceProvider() {
                @Override
                public String getProviderName() {
                    return "TEST";
                }

                @Override
                public Collection<String> sourceNames() {
                    return List.of("jobs");
                }

                @Override
                public Optional<DumStructuredSource> resolve(String sourceName) {
                    return Optional.of(source);
                }
            };
        }
    }

    private static final class CapturingContext implements DumConnectorContext {
        private final List<DumJobItemWithSession> items = new ArrayList<>();

        @Override
        public boolean addJobItem(DumJobItemWithSession item) {
            return items.add(item);
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            // nothing to finish
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
