/*
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/** Covers {@link DumStructuredRecord} builder validation, grounding, type checks and provenance. */
class DumStructuredRecordTest {

    private static DumStructuredSchema schema() {
        return DumStructuredSchema.builder()
                .field(DumStructuredField.builder("title").type(TurSEFieldType.TEXT).mandatory(true)
                        .build())
                .field(DumStructuredField.builder("tags").type(TurSEFieldType.STRING)
                        .multiValued(true).build())
                .field(DumStructuredField.builder("views").type(TurSEFieldType.INT).build())
                .field(DumStructuredField.builder("active").type(TurSEFieldType.BOOL).build())
                .build();
    }

    @Test
    void builderRejectsBlankIdAndNullSchema() {
        DumStructuredSchema schema = schema();
        assertThrows(IllegalArgumentException.class, () -> DumStructuredRecord.builder("  ", schema));
        assertThrows(IllegalArgumentException.class, () -> DumStructuredRecord.builder("id", null));
    }

    @Test
    void setRejectsUndeclaredField() {
        DumStructuredRecord.Builder builder = DumStructuredRecord.builder("id", schema());
        assertThrows(IllegalArgumentException.class, () -> builder.set("ghost", "x"));
    }

    @Test
    void groundingDropsBlankAndNullValues() {
        DumStructuredRecord rec = DumStructuredRecord.builder("id", schema())
                .set("title", null)
                .set("tags", " ")
                .build();

        assertFalse(rec.has("title"));
        assertFalse(rec.has("tags"));
        assertTrue(rec.values().isEmpty());
        assertEquals(List.of("title"), rec.missingMandatoryFields());
    }

    @Test
    void storesScalarAndWrapsMultiValued() {
        DumStructuredRecord rec = DumStructuredRecord.builder("id", schema())
                .set("title", "Hello")
                .set("tags", "one")
                .build();

        assertEquals("Hello", rec.values().get("title"));
        assertEquals(List.of("one"), rec.values().get("tags"));
        assertTrue(rec.missingMandatoryFields().isEmpty());
    }

    @Test
    void multiValuedKeepsCollectionDroppingBlanks() {
        DumStructuredRecord rec = DumStructuredRecord.builder("id", schema())
                .set("tags", List.of("a", " ", "b"))
                .build();

        assertEquals(List.of("a", "b"), rec.values().get("tags"));
    }

    @Test
    void singleValuedCollectionWithMultipleValuesThrows() {
        DumStructuredRecord.Builder builder = DumStructuredRecord.builder("id", schema());
        List<String> two = List.of("x", "y");
        assertThrows(IllegalArgumentException.class, () -> builder.set("title", two));
    }

    @Test
    void typeValidationRejectsNonNumericInt() {
        DumStructuredRecord.Builder builder = DumStructuredRecord.builder("id", schema());
        assertThrows(IllegalArgumentException.class, () -> builder.set("views", "not-a-number"));
    }

    @Test
    void typeValidationAcceptsNumberAndBoolean() {
        DumStructuredRecord rec = DumStructuredRecord.builder("id", schema())
                .set("views", 42)
                .set("active", "true")
                .build();

        assertEquals(42, rec.values().get("views"));
        assertEquals("true", rec.values().get("active"));
    }

    @Test
    void typeValidationRejectsNonBoolean() {
        DumStructuredRecord.Builder builder = DumStructuredRecord.builder("id", schema());
        assertThrows(IllegalArgumentException.class, () -> builder.set("active", "maybe"));
    }

    @Test
    void provenanceKeptForSetValueThenClearedWhenReSetWithoutIt() {
        DumStructuredRecord rec = DumStructuredRecord.builder("id", schema())
                .set("title", "A", DumFieldProvenance.aem("title"))
                .build();
        assertTrue(rec.provenance().containsKey("title"));

        DumStructuredRecord recleared = DumStructuredRecord.builder("id", schema())
                .set("title", "A", DumFieldProvenance.aem("title"))
                .set("title", "B")
                .build();
        assertFalse(recleared.provenance().containsKey("title"));
        assertEquals("B", recleared.values().get("title"));
    }

    @Test
    void contentTypeAndGettersExposeState() {
        DumStructuredSchema schema = schema();
        DumStructuredRecord rec = DumStructuredRecord.builder("doc-1", schema)
                .contentType("article")
                .set("title", "T")
                .build();

        assertEquals("doc-1", rec.id());
        assertSame(schema, rec.schema());
        assertEquals("article", rec.contentType());
        var values = rec.values();
        assertThrows(UnsupportedOperationException.class, () -> values.put("x", "y"));
    }
}
