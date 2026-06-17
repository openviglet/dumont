/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.commons.spi.structured.coverage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumInMemoryProvenanceHistoryStore;

/**
 * Verifies {@link DumContentTypeCoverageRecorder} (roadmap <strong>D25</strong>): records are
 * bucketed by content type and flushed to per-type history under the composite key, each carrying
 * that type's own field coverage.
 */
@DisplayName("DumContentTypeCoverageRecorder (D25)")
class DumContentTypeCoverageRecorderTest {

    private static final DumStructuredSchema ARTICLE = DumStructuredSchema.builder()
            .title("title").text("body").build();
    private static final DumStructuredSchema EVENT = DumStructuredSchema.builder()
            .title("title").string("startDate").build();

    private static DumStructuredRecord article(String id, boolean withBody) {
        DumStructuredRecord.Builder builder =
                DumStructuredRecord.builder(id, ARTICLE).contentType("article").set("title", "T " + id);
        if (withBody) {
            builder.set("body", "Body " + id);
        }
        return builder.build();
    }

    private static DumStructuredRecord event(String id) {
        return DumStructuredRecord.builder(id, EVENT).contentType("event")
                .set("title", "E " + id).set("startDate", "2026-06-17").build();
    }

    @Test
    @DisplayName("Should record per-type coverage under the composite key")
    void shouldRecordPerTypeCoverage() {
        DumContentTypeCoverageRecorder recorder = new DumContentTypeCoverageRecorder();
        DumInMemoryCoverageHistoryStore coverage = new DumInMemoryCoverageHistoryStore();
        DumInMemoryProvenanceHistoryStore provenance = new DumInMemoryProvenanceHistoryStore();

        recorder.add(article("a1", true));
        recorder.add(article("a2", false)); // body absent -> 50% body coverage
        recorder.add(event("e1"));

        assertFalse(recorder.isEmpty());
        recorder.flush("portal", coverage, provenance);

        DumCoverageSnapshot articleRun = coverage.latest("portal::article").orElseThrow();
        assertEquals(2, articleRun.documentCount());
        assertEquals(100, articleRun.field("title").orElseThrow().percent());
        assertEquals(50, articleRun.field("body").orElseThrow().percent());

        DumCoverageSnapshot eventRun = coverage.latest("portal::event").orElseThrow();
        assertEquals(1, eventRun.documentCount());
        assertEquals(100, eventRun.field("startDate").orElseThrow().percent());

        // The source-level key is never written by the recorder (the plugin owns that series).
        assertTrue(coverage.history("portal").isEmpty());
        assertEquals(2, provenance.history("portal::article").get(0).documentCount());
    }

    @Test
    @DisplayName("Should ignore records without a content type")
    void shouldIgnoreUntyped() {
        DumContentTypeCoverageRecorder recorder = new DumContentTypeCoverageRecorder();
        DumInMemoryCoverageHistoryStore coverage = new DumInMemoryCoverageHistoryStore();
        DumInMemoryProvenanceHistoryStore provenance = new DumInMemoryProvenanceHistoryStore();

        DumStructuredField title = DumStructuredField.title("title");
        DumStructuredSchema schema = DumStructuredSchema.builder().field(title).build();
        recorder.add(DumStructuredRecord.builder("u1", schema).set("title", "x").build());

        assertTrue(recorder.isEmpty());
        recorder.flush("portal", coverage, provenance);
        assertTrue(coverage.history("portal::null").isEmpty());
    }

    @Test
    @DisplayName("key() joins source and content type with the separator")
    void shouldBuildKey() {
        assertEquals("portal::article",
                DumContentTypeCoverageRecorder.key("portal", "article"));
    }
}
