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

package com.viglet.dumont.connector.commons.spi.structured.jsonapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumEmbeddingRole;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.jsonapi.DumJsonApiManifest.PagingMode;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/** Reading a JSON API manifest, and refusing one that cannot run (roadmap D92). */
class DumJsonApiManifestTest {

    static final String ADZUNA = """
            {
              "name": "adzuna", "version": 2,
              "request": { "url": "https://api.example/jobs/{page}?app_id={secret:appId}&n={size}" },
              "paging": { "mode": "page", "size": 50, "maxPages": 20, "totalPath": "count" },
              "budget": { "maxCallsPerRun": 20, "callsPerMinute": 25 },
              "records": "results",
              "id": "id",
              "fields": [
                { "name": "title", "type": "text", "role": "title", "mandatory": true },
                { "name": "company", "path": "company.display_name", "facet": true },
                { "name": "skills", "path": "skills.name", "multiValued": true }
              ]
            }
            """;

    @Test
    void parsesEveryPart() {
        DumJsonApiManifest manifest = DumJsonApiManifest.parse(ADZUNA);

        assertEquals("adzuna", manifest.name());
        assertEquals(2, manifest.version());
        assertEquals("GET", manifest.request().method());
        assertEquals(PagingMode.PAGE, manifest.paging().mode());
        assertEquals(1, manifest.paging().start(), "pages start at 1 unless declared");
        assertEquals(50, manifest.paging().size());
        assertEquals(20, manifest.paging().maxPages());
        assertEquals("count", manifest.paging().totalPath());
        assertEquals(20, manifest.budget().maxCallsPerRun());
        assertEquals(2400, manifest.budget().minIntervalMs());
        assertEquals("results", manifest.records());
        assertEquals("id", manifest.idPath());
        assertEquals(Set.of("appId"), manifest.secretNames());
    }

    @Test
    void theMappedFieldsAreTheSchema() {
        DumStructuredSchema schema = DumJsonApiManifest.parse(ADZUNA).schema();

        assertEquals(List.of("title", "company", "skills"),
                schema.fields().stream().map(DumStructuredField::name).toList());
        DumStructuredField title = schema.field("title").orElseThrow();
        assertEquals(TurSEFieldType.TEXT, title.type());
        assertEquals(DumEmbeddingRole.TITLE, title.embeddingRole());
        assertTrue(title.mandatory());
        assertTrue(schema.field("company").orElseThrow().facet());
        assertTrue(schema.field("skills").orElseThrow().multiValued());
    }

    @Test
    void aPostBodyObjectIsKeptAsItsJsonText() {
        DumJsonApiManifest manifest = DumJsonApiManifest.parse("""
                {"name": "bne", "request": {"method": "post", "url": "https://bne.example/vagas",
                  "body": {"pagina": "{page}", "filtro": "ti"}},
                 "paging": {"mode": "page"}, "records": "data.listVagas", "id": "id",
                 "fields": [{"name": "title"}]}
                """);

        assertEquals("POST", manifest.request().method());
        assertTrue(manifest.request().body().contains("\"pagina\":\"{page}\""));
        assertEquals(1, manifest.version(), "version defaults to 1");
    }

    @Test
    void refusesWhatCannotRun() {
        assertRefused("{not json", "must be a JSON object");
        assertRefused(ADZUNA.replace("\"id\": \"id\",", ""), "needs an id path");
        assertRefused(ADZUNA.replace("\"name\": \"adzuna\",", ""), "needs a name");
        assertRefused(ADZUNA.replace("{page}", "1"), "never uses {page}");
        assertRefused(ADZUNA.replace("{size}", "{limit}"), "unknown placeholder {limit}");
        assertRefused(ADZUNA.replace("{secret:appId}", "{secret}"), "names its secret");
        assertRefused(ADZUNA.replace("\"mode\": \"page\"", "\"mode\": \"cursor\""), "no cursorPath");
        assertRefused(ADZUNA.replace("\"mode\": \"page\"", "\"mode\": \"scroll\""), "unknown paging mode");
        assertRefused(ADZUNA.replace("\"path\": \"skills.name\"", "\"path\": \"company.display_name\"")
                .replace("\"name\": \"skills\"", "\"name\": \"company\""), "twice");
        assertRefused(ADZUNA.replace("\"type\": \"text\"", "\"type\": \"blob\""), "unknown type");
        assertRefused("""
                {"name": "g", "request": {"url": "https://x", "body": "{}"}, "id": "id",
                 "fields": [{"name": "title"}]}
                """, "only a POST request carries a body");
        assertRefused("""
                {"name": "o", "request": {"url": "https://x?o={offset}"}, "paging": {"mode": "offset"},
                 "id": "id", "fields": [{"name": "title"}]}
                """, "no page size");
    }

    private static void assertRefused(String json, String fragment) {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> DumJsonApiManifest.parse(json));
        assertTrue(e.getMessage().contains(fragment),
                () -> "expected '%s' in: %s".formatted(fragment, e.getMessage()));
    }
}
