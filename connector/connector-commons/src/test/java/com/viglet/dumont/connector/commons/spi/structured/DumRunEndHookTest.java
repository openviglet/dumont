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

/** A structured source hears how its run ended, after the run finished (roadmap D115). */
class DumRunEndHookTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder().text("title").build();

    private final List<String> events = new ArrayList<>();

    @Test
    void aCompleteRunEndsSweptAfterTheFinish() {
        run(new Source(events, true, false, false));

        assertEquals(List.of("finish swept", "end " + DumRunEnd.complete()), events);
    }

    @Test
    void aRunWhoseDiscoveryStoppedEndsPartialAndUnswept() {
        run(new Source(events, false, false, false));

        assertEquals(List.of("finish standalone",
                "end " + DumRunEnd.stopped("discovery stopped before the end of the source")), events);
    }

    @Test
    void anAbortedRunStillEnds() {
        run(new Source(events, true, false, true));

        assertEquals(List.of("finish standalone", "end " + DumRunEnd.stopped("aborted by an exception")), events);
    }

    @Test
    void anIncrementalRunEndsUnsweptAndCompleteWhenItReachedTheEnd() {
        run(new Source(events, true, true, false));

        assertEquals(List.of("finish standalone", "end " + new DumRunEnd(false, false, null)), events);
    }

    @Test
    void aHookThatThrowsDoesNotFailTheRun() {
        Source throwing = new Source(events, true, false, false) {
            @Override
            public void onRunEnd(DumRunEnd end) {
                throw new IllegalStateException("boom");
            }
        };

        run(throwing);

        assertEquals(List.of("finish swept"), events);
    }

    private void run(Source source) {
        new Plugin(new Recording(events), source).indexAll("jobs");
    }

    private static class Source implements DumStructuredSource {
        private final List<String> events;
        private final boolean discoveryComplete;
        private final boolean incremental;
        private final boolean throwing;

        Source(List<String> events, boolean discoveryComplete, boolean incremental, boolean throwing) {
            this.events = events;
            this.discoveryComplete = discoveryComplete;
            this.incremental = incremental;
            this.throwing = throwing;
        }

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
            if (throwing) {
                throw new IllegalStateException("partner down");
            }
            return List.of("live-1");
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            return Optional.of(DumStructuredRecord.builder(contentId, SCHEMA).set("title", "Analyst").build());
        }

        @Override
        public boolean discoveryComplete() {
            return discoveryComplete;
        }

        @Override
        public boolean incremental() {
            return incremental;
        }

        @Override
        public void onRunEnd(DumRunEnd end) {
            events.add("end " + end);
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

    private record Recording(List<String> events) implements DumConnectorContext {

        @Override
        public boolean addJobItem(DumJobItemWithSession item) {
            return true;
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
