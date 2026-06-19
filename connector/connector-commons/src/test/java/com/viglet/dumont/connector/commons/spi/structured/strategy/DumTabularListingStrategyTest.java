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
 */

package com.viglet.dumont.connector.commons.spi.structured.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/** Unit test for {@link DumTabularListingStrategy} (roadmap D02): the SIGAA table shape. */
class DumTabularListingStrategyTest {

    private static final String LISTING = "https://uni.edu/cursos";

    private static final Map<String, String> PAGES = Map.of(LISTING, """
            <html><body>
            <table class="listagem">
              <thead><tr><th>Código</th><th>Curso</th><th>Área</th></tr></thead>
              <tbody>
                <tr><td>CC101</td><td><a href="/curso/CC101">Algoritmos</a></td><td>Computação</td></tr>
                <tr><td></td><td></td><td></td></tr>
                <tr><td>MAT201</td><td><a href="/curso/MAT201">Cálculo</a></td><td>Matemática</td></tr>
              </tbody>
            </table>
            </body></html>
            """);

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .string("code")
            .field(DumStructuredField.builder("title").type(TurSEFieldType.TEXT).mandatory(true).build())
            .string("url")
            .string("area")
            .build();

    private DumTabularListingStrategy strategy() {
        return DumTabularListingStrategy.builder(DumHtmlFetcher.ofPages(PAGES), SCHEMA)
                .listing(LISTING)
                .rows("table.listagem > tbody > tr")
                .column("code", 0)
                .column(DumColumnRule.selector("title", "td:nth-child(2) a"))
                .column(DumColumnRule.selector("url", "td:nth-child(2) a", "abs:href"))
                .column("area", 2)
                .idField("code")
                .build();
    }

    @Test
    void discoversOneIdPerDataRowSkippingHeaderAndBlankRows() {
        assertEquals(List.of("CC101", "MAT201"), strategy().discover());
    }

    @Test
    void extractsColumnsByIndexAndSelector() {
        DumStructuredRecord rec = strategy().extract("CC101").orElseThrow();

        assertEquals("CC101", rec.id());
        assertEquals("CC101", rec.values().get("code"));
        assertEquals("Algoritmos", rec.values().get("title"));
        assertEquals("https://uni.edu/curso/CC101", rec.values().get("url"));
        assertEquals("Computação", rec.values().get("area"));
    }

    @Test
    void returnsEmptyForUnknownId() {
        assertTrue(strategy().extract("UNKNOWN").isEmpty());
    }
}
