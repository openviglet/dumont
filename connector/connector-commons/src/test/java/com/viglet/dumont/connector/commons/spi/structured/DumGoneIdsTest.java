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
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnrichmentChain;

/** Any source can deindex what it found gone, without the incremental mode (roadmap D114). */
class DumGoneIdsTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder().text("title").build();

    private final Recording context = new Recording();

    @Test
    void aCompleteRunDeindexesWhatTheSourceFoundGoneAndStillSweeps() {
        new Plugin(context, new Source(true, List.of("dead-1", "dead-2"))).indexAll("jobs");

        assertEquals(List.of("delete [dead-1, dead-2] on [Sample]", "finish swept"), context.events);
    }

    @Test
    void aRunThatStopsShortStillDeindexesWhatItFoundGone() {
        new Plugin(context, new Source(false, List.of("dead-1"))).indexAll("jobs");

        assertEquals(List.of("delete [dead-1] on [Sample]", "finish standalone"), context.events);
    }

    @Test
    void nothingFoundGoneQueuesNoDelete() {
        new Plugin(context, new Source(true, List.of())).indexAll("jobs");

        assertEquals(List.of("finish swept"), context.events);
    }

    private record Source(boolean discoveryComplete, List<String> goneIds) implements DumStructuredSource {

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
            return List.of("live-1");
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            return Optional.of(DumStructuredRecord.builder(contentId, SCHEMA).set("title", "Analyst").build());
        }
    }

    private static final class Plugin extends DumStructuredSourcePlugin implements DumStructuredSourceProvider {
        private final DumStructuredSource source;

        Plugin(DumConnectorContext context, DumStructuredSource source) {
            super(context, DumEnrichmentChain.empty());
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

    private static final class Recording implements DumConnectorContext {
        private final List<String> events = new ArrayList<>();

        @Override
        public boolean addJobItem(DumJobItemWithSession item) {
            return true;
        }

        @Override
        public int deIndexById(DumConnectorSession session, Collection<String> objectIds, List<String> sites) {
            events.add("delete " + objectIds + " on " + sites);
            return objectIds.size();
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            events.add(standalone ? "finish standalone" : "finish swept");
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
