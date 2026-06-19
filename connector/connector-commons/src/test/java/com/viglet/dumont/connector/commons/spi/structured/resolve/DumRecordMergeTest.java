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

package com.viglet.dumont.connector.commons.spi.structured.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/** Unit test for the grounding-safe cross-source merge (roadmap D18). */
class DumRecordMergeTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .field(DumStructuredField.builder("code").type(TurSEFieldType.STRING).build())
            .text("title")
            .text("workload")
            .build();

    @Test
    void rekeyRewritesIdAndStampsSourceOnEveryField() {
        DumStructuredRecord rec = DumStructuredRecord.builder("a-1", SCHEMA)
                .set("code", "CS101", DumFieldProvenance.css(".code"))
                .set("title", "Algorithms", DumFieldProvenance.css("h1"))
                .build();

        DumStructuredRecord rekeyed = DumRecordMerge.rekey(rec, "cs101", "catalog-a");

        assertEquals("cs101", rekeyed.id());
        assertEquals("catalog-a", rekeyed.provenance().get("code").source());
        assertEquals("catalog-a", rekeyed.provenance().get("title").source());
        assertEquals("css", rekeyed.provenance().get("title").strategy(),
                "the original strategy/selector are preserved");
    }

    @Test
    void mergeFillsGapsButNeverOverwritesTheFirstContributor() {
        DumStructuredRecord base = DumRecordMerge.rekey(DumStructuredRecord.builder("a-1", SCHEMA)
                .set("code", "CS101", DumFieldProvenance.css(".code"))
                .set("title", "Algorithms", DumFieldProvenance.css("h1"))
                .build(), "cs101", "catalog-a");
        DumStructuredRecord incoming = DumStructuredRecord.builder("b-7", SCHEMA)
                .set("title", "Intro to Algorithms", DumFieldProvenance.css("h1"))
                .set("workload", "60h", DumFieldProvenance.css(".workload"))
                .build();

        DumStructuredRecord merged = DumRecordMerge.merge(base, incoming, "catalog-b");

        assertEquals("Algorithms", merged.values().get("title"), "first contributor's value wins");
        assertEquals("catalog-a", merged.provenance().get("title").source(),
                "and its source is preserved");
        assertEquals("catalog-a", merged.provenance().get("title").source());
        // The gap is filled by the incoming source, tagged with that source.
        assertEquals("catalog-b", merged.provenance().get("workload").source());
        assertEquals(".workload", merged.provenance().get("workload").selector());
    }

    @Test
    void mergeDropsIncomingFieldsTheCanonicalSchemaDoesNotDeclare() {
        DumStructuredSchema wider = DumStructuredSchema.builder()
                .text("title").text("campus").build();
        DumStructuredRecord base = DumRecordMerge.rekey(DumStructuredRecord.builder("a-1", SCHEMA)
                .set("title", "Algorithms", DumFieldProvenance.css("h1")).build(), "cs101", "catalog-a");
        DumStructuredRecord incoming = DumStructuredRecord.builder("b-7", wider)
                .set("campus", "Main", DumFieldProvenance.css(".campus")).build();

        DumStructuredRecord merged = DumRecordMerge.merge(base, incoming, "catalog-b");

        assertFalse(merged.has("campus"),
                "a field the canonical (base) schema cannot express is not injected");
    }

    @Test
    void canonicalKeyNormalizesAndJoinsFields() {
        DumCanonicalKey key = DumCanonicalKey.field("code");
        DumStructuredRecord rec = DumStructuredRecord.builder("a-1", SCHEMA)
                .set("code", "  CS 101 ", DumFieldProvenance.css(".code")).build();

        assertEquals("cs 101", key.canonicalId(null, rec).orElseThrow());
    }

    @Test
    void canonicalKeyIsEmptyWhenAKeyFieldIsAbsent() {
        DumCanonicalKey key = DumCanonicalKey.field("code");
        DumStructuredRecord rec = DumStructuredRecord.builder("a-1", SCHEMA)
                .set("title", "Algorithms", DumFieldProvenance.css("h1")).build();

        assertTrue(key.canonicalId(null, rec).isEmpty());
    }
}
