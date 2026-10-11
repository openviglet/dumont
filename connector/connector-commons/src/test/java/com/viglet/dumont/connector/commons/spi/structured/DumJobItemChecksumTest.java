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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnrichmentChain;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;

/** A structured job item carries a checksum that only a change to what it publishes moves (D127). */
class DumJobItemChecksumTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder().text("title").build();

    @Test
    void mapOrderDoesNotMoveTheChecksum() {
        Map<String, Object> ab = new LinkedHashMap<>();
        ab.put("a", "1");
        ab.put("b", Map.of("x", 1, "y", 2));
        Map<String, Object> ba = new LinkedHashMap<>();
        ba.put("b", new LinkedHashMap<>(Map.of("y", 2, "x", 1)));
        ba.put("a", "1");

        assertEquals(DumJobItemChecksum.of(item(ab)), DumJobItemChecksum.of(item(ba)));
    }

    @Test
    void aChangedValueMovesTheChecksum() {
        assertNotEquals(DumJobItemChecksum.of(item(Map.of("title", "Analyst"))),
                DumJobItemChecksum.of(item(Map.of("title", "Engineer"))));
    }

    @Test
    void twoRunsOfAnUnchangedSourcePublishTheSameChecksum() {
        Map<String, String> titles = new HashMap<>(Map.of("job-1", "Analyst"));
        Recording context = new Recording();
        Plugin plugin = new Plugin(context, titles);

        plugin.indexAll("jobs");
        plugin.indexAll("jobs");
        titles.put("job-1", "Engineer");
        plugin.indexAll("jobs");

        List<String> checksums = context.items.stream().map(TurSNJobItem::getChecksum).toList();
        assertNotNull(checksums.get(0));
        assertEquals(checksums.get(0), checksums.get(1));
        assertNotEquals(checksums.get(1), checksums.get(2));
    }

    private static TurSNJobItem item(Map<String, Object> attributes) {
        return new TurSNJobItem(TurSNJobAction.CREATE, List.of("Sample"), Locale.US,
                new HashMap<>(attributes), List.of());
    }

    private record Source(Map<String, String> titles) implements DumStructuredSource {
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
            return List.copyOf(titles.keySet());
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            return Optional.of(DumStructuredRecord.builder(contentId, SCHEMA)
                    .set("title", titles.get(contentId)).build());
        }
    }

    private static final class Plugin extends DumStructuredSourcePlugin implements DumStructuredSourceProvider {
        private final DumStructuredSource source;

        Plugin(DumConnectorContext context, Map<String, String> titles) {
            super(context, DumEnrichmentChain.empty());
            this.source = new Source(titles);
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
        private final List<TurSNJobItem> items = new ArrayList<>();

        @Override
        public boolean addJobItem(DumJobItemWithSession item) {
            items.add(item.turSNJobItem());
            return true;
        }

        @Override
        public void finishIndexing(com.viglet.dumont.connector.commons.DumConnectorSession session,
                boolean standalone) {
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
