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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/** Unit test for {@link DumHtmlFieldExtractor} (roadmap D02): detail page -> grounded record. */
class DumHtmlFieldExtractorTest {

    private static final String DETAIL = "https://uni.edu/curso/algoritmos";

    private static final Map<String, String> PAGES = Map.of(DETAIL, """
            <html><head><title>Catalog</title></head><body>
              <h1 class="title">Algoritmos</h1>
              <div class="desc">Sorting and complexity</div>
              <ul class="areas"><li>Computer Science</li><li>Mathematics</li></ul>
              <a class="permalink" href="/curso/algoritmos">Permalink</a>
            </body></html>
            """);

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .field(DumStructuredField.builder("title").type(TurSEFieldType.TEXT).mandatory(true).build())
            .text("description")
            .facet("area")
            .string("url")
            .text("subtitle")
            .build();

    private final DumHtmlFetcher fetcher = DumHtmlFetcher.ofPages(PAGES);

    @Test
    void extractsDeclaredFieldsAndGroundsAbsentOnes() {
        DumHtmlFieldExtractor extractor = DumHtmlFieldExtractor.builder(fetcher, SCHEMA)
                .text("title", "h1.title")
                .text("description", "div.desc")
                .multiText("area", "ul.areas li")
                .attr("url", "a.permalink", "abs:href")
                .text("subtitle", "h2.missing")
                .build();

        Optional<DumStructuredRecord> result = extractor.extract(DETAIL);

        assertTrue(result.isPresent());
        DumStructuredRecord rec = result.get();
        assertEquals(DETAIL, rec.id());
        assertEquals("Algoritmos", rec.values().get("title"));
        assertEquals("Sorting and complexity", rec.values().get("description"));
        assertEquals(List.of("Computer Science", "Mathematics"), rec.values().get("area"));
        assertEquals(DETAIL, rec.values().get("url"));
        // No <h2.missing> on the page -> the optional field is omitted, not "".
        assertFalse(rec.has("subtitle"));
        assertTrue(rec.missingMandatoryFields().isEmpty());
    }
}
