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

/** A source throwing outside its extraction ends its own run, not the crawl (roadmap D129). */
class DumCrawlIsolationTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder().text("title").build();

    @Test
    void aSourceWhoseFinishThrowsDoesNotStopTheNextOne() {
        List<String> finished = new ArrayList<>();
        DumConnectorContext context = new DumConnectorContext() {
            @Override
            public void finishIndexing(DumConnectorSession session, boolean standalone) {
                if ("broken".equals(session.getSource())) {
                    throw new IllegalStateException("Column TENANT_ID not found");
                }
                finished.add(session.getSource());
            }

            @Override
            public boolean addJobItem(DumJobItemWithSession item) {
                return true;
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
        };

        new Plugin(context, List.of(new Source("broken"), new Source("fine"))).crawl();

        assertEquals(List.of("fine"), finished);
    }

    private record Source(String name) implements DumStructuredSource {
        @Override
        public String getSourceName() {
            return name;
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
    }

    private static final class Plugin extends DumStructuredSourcePlugin implements DumStructuredSourceProvider {
        private final List<Source> sources;

        Plugin(DumConnectorContext context, List<Source> sources) {
            super(context, DumEnrichmentChain.empty());
            this.sources = sources;
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
            return sources.stream().map(Source::name).toList();
        }

        @Override
        public Optional<DumStructuredSource> resolve(String sourceName) {
            return sources.stream().filter(s -> s.name().equals(sourceName))
                    .map(DumStructuredSource.class::cast).findFirst();
        }
    }
}
