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
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourcePlugin;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;

/** An enricher's end-of-run summary reaches the run record (roadmap D100). */
class DumEnricherRunEndTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .string("title").string("city").build();

    @Test
    void theChainCollectsNotesInOrderAndSurvivesAFailingHook() {
        DumEnricher first = runEnd("first", () -> List.of("first note"));
        DumEnricher broken = runEnd("broken", () -> {
            throw new IllegalStateException("boom");
        });
        DumEnricher last = runEnd("last", () -> List.of("", "last note"));

        assertEquals(List.of("first note", "last note"),
                DumEnrichmentChain.of(first, broken, last).runEnd(source(List.of())));
    }

    @Test
    void aCountingEnrichersSummaryJoinsTheRecordedOutcomeAndResetsPerRun() {
        UnresolvedCounter counter = new UnresolvedCounter();
        CapturingContext context = new CapturingContext();
        Plugin plugin = new Plugin(context, DumEnrichmentChain.of(counter),
                source(List.of(rec("1", "Recife"), rec("2", "Atlantis"), rec("3", "Lemuria"))));

        plugin.indexAll("jobs");
        assertEquals(List.of("2 location(s) unresolved"), context.notes);
        assertEquals(false, context.partial);

        context.notes = null;
        plugin.indexAll("jobs");
        assertEquals(List.of("2 location(s) unresolved"), context.notes, "the count restarts each run");
    }

    // --- helpers -------------------------------------------------------------------------------

    /** Counts records whose city it cannot resolve, and reports the count once per run. */
    private static final class UnresolvedCounter implements DumEnricher {
        private int unresolved;

        @Override
        public String name() {
            return "unresolved-counter";
        }

        @Override
        public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord rec) {
            if (!"Recife".equals(rec.values().get("city"))) {
                unresolved++;
            }
            return Map.of();
        }

        @Override
        public List<String> onRunEnd(DumStructuredSource source) {
            List<String> notes = unresolved == 0 ? List.of()
                    : List.of("%d location(s) unresolved".formatted(unresolved));
            unresolved = 0;
            return notes;
        }
    }

    private static DumEnricher runEnd(String name, java.util.function.Supplier<List<String>> notes) {
        return new DumEnricher() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord rec) {
                return Map.of();
            }

            @Override
            public List<String> onRunEnd(DumStructuredSource source) {
                return notes.get();
            }
        };
    }

    private static DumStructuredRecord rec(String id, String city) {
        return DumStructuredRecord.builder(id, SCHEMA).set("title", "Job " + id).set("city", city).build();
    }

    private static DumStructuredSource source(List<DumStructuredRecord> records) {
        return new DumStructuredSource() {
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
                return List.of();
            }

            @Override
            public DumStructuredSchema schema() {
                return SCHEMA;
            }

            @Override
            public List<String> discover() {
                return records.stream().map(DumStructuredRecord::id).toList();
            }

            @Override
            public Optional<DumStructuredRecord> extract(String contentId) {
                return records.stream().filter(r -> r.id().equals(contentId)).findFirst();
            }
        };
    }

    private static final class Plugin extends DumStructuredSourcePlugin {
        private final DumStructuredSource source;

        Plugin(DumConnectorContext context, DumEnrichmentChain chain, DumStructuredSource source) {
            super(context, chain);
            this.source = source;
        }

        @Override
        protected DumStructuredSourceProvider provider() {
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
        private List<String> notes;
        private Boolean partial;

        @Override
        public void recordRunOutcome(DumConnectorSession session, boolean partial, List<String> notes) {
            this.partial = partial;
            this.notes = List.copyOf(notes);
        }

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
