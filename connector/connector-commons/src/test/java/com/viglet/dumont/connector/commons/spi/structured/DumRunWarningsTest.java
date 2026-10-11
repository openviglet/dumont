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
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/** A source's warnings reach the run record, on a complete run as on a partial one (D130). */
class DumRunWarningsTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder().text("title").build();
    private static final String WARNING = "new postings fell 92% under the 7-day median";

    @Test
    void aCompleteRunWithOnlyAWarningRecordsItAsNotPartial() {
        Recording context = new Recording();

        new Plugin(context, true).indexAll("jobs");

        assertEquals(List.of("complete " + List.of() + " " + List.of(WARNING)), context.outcomes);
    }

    @Test
    void aPartialRunCarriesItsWarningBesideItsReason() {
        Recording context = new Recording();

        new Plugin(context, false).indexAll("jobs");

        assertEquals(1, context.outcomes.size());
        assertTrue(context.outcomes.getFirst().startsWith("partial "));
        assertTrue(context.outcomes.getFirst().endsWith(" " + List.of(WARNING)));
    }

    @Test
    void aFieldWhoseCoverageCollapsedSinceTheLastRunWarnsOnThisOne() {
        Recording context = new Recording();
        Drifting source = new Drifting();
        Plugin plugin = new Plugin(context, source);

        plugin.indexAll("jobs");
        source.titled = false;
        plugin.indexAll("jobs");

        assertEquals(List.of("complete " + List.of() + " "
                + List.of("coverage of field 'title' dropped from 100% to 0%")), context.outcomes);
    }

    /** A source whose records carry a title until a layout change drops it. */
    private static final class Drifting implements DumStructuredSource {
        private boolean titled = true;

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
            return List.of("job-1", "job-2");
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            DumStructuredRecord.Builder rec = DumStructuredRecord.builder(contentId, SCHEMA);
            if (titled) {
                rec.set("title", "Analyst");
            }
            return Optional.of(rec.build());
        }
    }

    private record Source(boolean discoveryComplete) implements DumStructuredSource {
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
            return List.of("job-1");
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            return Optional.of(DumStructuredRecord.builder(contentId, SCHEMA).set("title", "Analyst").build());
        }

        @Override
        public List<String> runWarnings() {
            return List.of(WARNING);
        }
    }

    private static final class Plugin extends DumStructuredSourcePlugin implements DumStructuredSourceProvider {
        private final DumStructuredSource source;

        Plugin(DumConnectorContext context, boolean discoveryComplete) {
            this(context, new Source(discoveryComplete));
        }

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
        private final List<String> outcomes = new ArrayList<>();

        @Override
        public void recordRunOutcome(DumConnectorSession session, boolean partial, List<String> notes,
                List<String> warnings) {
            outcomes.add((partial ? "partial " : "complete ") + notes + " " + warnings);
        }

        @Override
        public boolean addJobItem(DumJobItemWithSession item) {
            return true;
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            // Nothing to finish.
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
