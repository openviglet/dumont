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

/** A source and its enrichers hear of each document the connector deindexes (roadmap D113). */
class DumDeindexCallbackTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder().text("title").build();

    private final List<String> heard = new ArrayList<>();

    @Test
    void theSweepsRemovalsReachTheSourceAndTheEnrichers() {
        Source source = new Source();
        DumEnricher listening = new DumEnricher() {
            @Override
            public String name() {
                return "listening";
            }

            @Override
            public Map<String, Object> enrich(DumStructuredSource s, DumStructuredRecord rec) {
                return Map.of();
            }

            @Override
            public void onDeindexed(DumStructuredSource s, String documentId) {
                heard.add("enricher " + documentId);
            }
        };
        Plugin plugin = new Plugin(new SweepingContext(List.of("old-1", "old-2")), source,
                DumEnrichmentChain.of(listening));

        plugin.indexAll("jobs");

        assertEquals(List.of("source old-1", "enricher old-1", "source old-2", "enricher old-2"), heard);
    }

    @Test
    void aSourceThatThrowsStillLetsTheEnrichersHear() {
        Source source = new Source() {
            @Override
            public void deindexed(String documentId) {
                throw new IllegalStateException("boom");
            }
        };
        DumEnricher listening = new DumEnricher() {
            @Override
            public String name() {
                return "listening";
            }

            @Override
            public Map<String, Object> enrich(DumStructuredSource s, DumStructuredRecord rec) {
                return Map.of();
            }

            @Override
            public void onDeindexed(DumStructuredSource s, String documentId) {
                heard.add("enricher " + documentId);
            }
        };

        new Plugin(new SweepingContext(List.of("old-1")), source, DumEnrichmentChain.of(listening))
                .indexAll("jobs");

        assertEquals(List.of("enricher old-1"), heard);
    }

    private class Source implements DumStructuredSource {

        @Override
        public String getSourceName() {
            return "jobs";
        }

        @Override
        public String getProviderName() {
            return "JOBS";
        }

        @Override
        public Collection<String> getSiteNames() {
            return List.of("Sample");
        }

        @Override
        public DumStructuredSchema schema() {
            return SCHEMA;
        }

        @Override
        public List<String> discover() {
            return List.of("new-1");
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            return Optional.of(DumStructuredRecord.builder(contentId, SCHEMA).set("title", "Analyst").build());
        }

        @Override
        public void deindexed(String documentId) {
            heard.add("source " + documentId);
        }
    }

    private static final class Plugin extends DumStructuredSourcePlugin implements DumStructuredSourceProvider {
        private final DumStructuredSource source;

        Plugin(DumConnectorContext context, DumStructuredSource source, DumEnrichmentChain chain) {
            super(context, chain);
            this.source = source;
        }

        @Override
        protected DumStructuredSourceProvider provider() {
            return this;
        }

        @Override
        public String getProviderName() {
            return "JOBS";
        }

        @Override
        public Collection<String> sourceNames() {
            return List.of(source.getSourceName());
        }

        @Override
        public Optional<DumStructuredSource> resolve(String sourceName) {
            return source.getSourceName().equals(sourceName) ? Optional.of(source) : Optional.empty();
        }
    }

    /** A context whose swept finish deletes {@code stale}, the way the connector's sweep reports it. */
    private record SweepingContext(List<String> stale) implements DumConnectorContext {

        @Override
        public boolean addJobItem(DumJobItemWithSession item) {
            return true;
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            if (!standalone) {
                stale.forEach(id -> session.deindexed(id, List.of("Sample"), session.getLocale()));
            }
        }

        @Override
        public List<DumConnectorIndexing> getIndexingItem(String objectId, String source, String provider) {
            return List.of();
        }

        @Override
        public List<String> getObjectIdByDependency(String source, String provider,
                List<String> dependenciesObjectIdList) {
            return List.of();
        }
    }
}
