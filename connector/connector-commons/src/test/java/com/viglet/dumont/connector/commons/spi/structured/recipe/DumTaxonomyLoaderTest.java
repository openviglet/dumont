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

package com.viglet.dumont.connector.commons.spi.structured.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;

/** Multi-column reference tables as versioned taxonomies, read from resources (roadmap D99). */
class DumTaxonomyLoaderTest {

    private static final String RESOURCE = "taxonomy/municipios-sample.json";

    @Test
    void aResourceBecomesAVersionedTaxonomyWithAttributes() {
        DumTaxonomy taxonomy = DumTaxonomyLoader.fromResource(getClass().getClassLoader(), RESOURCE);

        assertEquals("municipios-sample", taxonomy.name());
        assertEquals(2, taxonomy.version());
        assertEquals(3, taxonomy.size());
        assertEquals("2026-10-01", taxonomy.downloadedAt());
        assertTrue(taxonomy.source().startsWith("https://servicodados.ibge.gov.br"));
        assertEquals(Optional.of("Recife"), taxonomy.canonical("2611606"));
        assertEquals(Map.of("uf", "PE"), taxonomy.attributes("2611606"));
        assertEquals(Optional.of("true"), taxonomy.attribute("5300108", "capital"));
        assertEquals(Map.of("2611606", "PE", "3550308", "SP", "5300108", "DF"),
                taxonomy.attributeTable("uf"));
        assertEquals(Map.of(), taxonomy.attributes("9999999"));
    }

    @Test
    void aFlatEntriesFileStillLoads() {
        DumTaxonomy taxonomy = DumTaxonomyLoader.fromJson("""
                {"name": "areas", "version": 1, "entries": {"Comp. Sci.": "Computer Science"}}
                """);

        assertEquals(Optional.of("Computer Science"), taxonomy.canonical("Comp. Sci."));
        assertEquals(Map.of(), taxonomy.attributes("Comp. Sci."));
    }

    @Test
    void refusesAFileThatCannotBeTrusted() {
        assertRefused("{nope", "JSON object");
        assertRefused("""
                {"name": "t", "version": 1, "canonical": "nome", "rows": []}
                """, "'key' column");
        assertRefused("""
                {"name": "t", "version": 1, "key": "id", "canonical": "nome", "count": 2,
                 "rows": [{"id": 1, "nome": "A"}]}
                """, "declares 2 rows but holds 1");
        assertRefused("""
                {"name": "t", "version": 1, "key": "id", "canonical": "nome", "rows": [{"id": 1}]}
                """, "row 0 lacks");
        assertRefused("""
                {"name": "t", "version": 1, "key": "id", "canonical": "nome", "rows": "items"}
                """, "no row array under 'items'");
        assertThrows(IllegalArgumentException.class,
                () -> DumTaxonomyLoader.fromResource(getClass().getClassLoader(), "taxonomy/missing.json"));
    }

    @Test
    void anApplicationCanWriteAnAttributeInsteadOfTheCanonicalValue() {
        DumRecipeLibrary library = new DumRecipeLibrary()
                .register(DumTaxonomyLoader.fromResource(getClass().getClassLoader(), RESOURCE));
        DumStructuredSchema schema = DumStructuredSchema.builder()
                .string("ibge").string("city").string("uf").build();
        DumRecipeRef ref = DumRecipeRef.latest("municipios-sample");
        DumStructuredRecord rec = DumStructuredRecord.builder("job-1", schema).set("ibge", "2611606").build();

        Map<String, Object> city = new DumTaxonomyEnricher(library,
                new DumTaxonomyApplication(ref, "ibge", "city")).enrich(null, rec);
        Map<String, Object> uf = new DumTaxonomyEnricher(library,
                new DumTaxonomyApplication(ref, "ibge", "uf", "uf")).enrich(null, rec);

        assertEquals("Recife", city.get("city"));
        assertEquals("PE", uf.get("uf"));
        assertEquals("taxonomy:municipios-sample.uf:ibge->uf",
                new DumTaxonomyApplication(ref, "ibge", "uf", "uf").enricherName());
    }

    private static void assertRefused(String json, String fragment) {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> DumTaxonomyLoader.fromJson(json));
        assertTrue(e.getMessage().contains(fragment), () -> "expected '%s' in: %s".formatted(fragment, e.getMessage()));
    }
}
