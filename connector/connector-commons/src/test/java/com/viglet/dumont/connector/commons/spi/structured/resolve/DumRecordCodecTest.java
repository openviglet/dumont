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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/** A record survives the trip through text a persistent resolution index makes (roadmap D109). */
class DumRecordCodecTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .text("title")
            .field(DumStructuredField.builder("seats").type(TurSEFieldType.INT).build())
            .field(DumStructuredField.builder("credits").type(TurSEFieldType.LONG).build())
            .field(DumStructuredField.builder("fee").type(TurSEFieldType.DOUBLE).build())
            .field(DumStructuredField.builder("price").type(TurSEFieldType.CURRENCY).build())
            .field(DumStructuredField.builder("open").type(TurSEFieldType.BOOL).build())
            .field(DumStructuredField.builder("updated").type(TurSEFieldType.DATE).build())
            .field(DumStructuredField.builder("legacyDate").type(TurSEFieldType.DATE).build())
            .field(DumStructuredField.builder("starts").type(TurSEFieldType.DATE).build())
            .field(DumStructuredField.builder("zoned").type(TurSEFieldType.DATE).build())
            .field(DumStructuredField.builder("tags").type(TurSEFieldType.STRING).multiValued(true).build())
            .build();

    @Test
    void everyValueKeepsItsTypeAndItsProvenance() {
        DumFieldProvenance merged = new DumFieldProvenance("css", "h1", 0.75, "catalog-b");
        DumStructuredRecord rec = DumStructuredRecord.builder("a-1", SCHEMA).contentType("course")
                .set("title", "Algorithms", merged)
                .set("seats", 30, DumFieldProvenance.tabular("seats"))
                .set("credits", 4L)
                .set("fee", 12.5)
                .set("price", new BigDecimal("199.90"))
                .set("open", true)
                .set("updated", Instant.parse("2026-10-11T10:00:00Z"))
                .set("legacyDate", new Date(1_700_000_000_000L))
                .set("starts", LocalDate.of(2026, 11, 3))
                .set("zoned", ZonedDateTime.parse("2026-10-11T10:00:00-03:00[America/Sao_Paulo]"))
                .set("tags", List.of("cs", "intro"))
                .transientValue("raw", "  Algorithms ")
                .transientValue("unsupported", new Object())
                .build();

        DumStructuredRecord back = DumRecordCodec.decode(DumRecordCodec.encode(rec), SCHEMA);

        assertEquals("a-1", back.id());
        assertEquals("course", back.contentType());
        assertEquals(rec.values(), back.values(), "values and their Java types survive");
        rec.values().forEach((field, value) -> assertEquals(value.getClass(), back.values().get(field).getClass(),
                "type of " + field));
        assertEquals(rec.provenance(), back.provenance());
        assertEquals(Map.of("raw", "  Algorithms "), back.transients(),
                "a transient of an unsupported type is left out");
    }

    @Test
    void aFieldTheSchemaNoLongerDeclaresIsDropped() {
        DumStructuredRecord rec = DumStructuredRecord.builder("a-1", SCHEMA).set("title", "Algorithms")
                .set("seats", 30).build();
        DumStructuredSchema narrower = DumStructuredSchema.builder().text("title").build();

        DumStructuredRecord back = DumRecordCodec.decode(DumRecordCodec.encode(rec), narrower);

        assertEquals(Map.of("title", "Algorithms"), back.values());
        assertFalse(back.has("seats"));
        assertNull(back.contentType());
    }
}
