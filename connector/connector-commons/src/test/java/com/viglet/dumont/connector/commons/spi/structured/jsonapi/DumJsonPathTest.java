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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/** The path language JSON API manifests map fields with (roadmap D92). */
class DumJsonPathTest {

    private static final JSONObject JOB = new JSONObject("""
            {"id": 7, "title": "Engineer", "company": {"display_name": "Acme"},
             "locations": [{"city": "Recife"}, {"city": "Natal"}],
             "skills": [{"name": "java", "tags": ["a", "b"]}, {"name": "sql", "tags": ["c"]}],
             "salary": null, "data": {"listVagas": [{"id": 1}]}}
            """);

    @Test
    void readsNestedKeys() {
        assertEquals("Acme", DumJsonPath.compile("company.display_name").read(JOB));
        assertEquals(7, DumJsonPath.compile("id").read(JOB));
    }

    @Test
    void readsAnArrayIndex() {
        assertEquals("Natal", DumJsonPath.compile("locations[1].city").read(JOB));
        assertNull(DumJsonPath.compile("locations[5].city").read(JOB));
    }

    @Test
    void aKeyOverAnArrayMapsEveryElement() {
        assertEquals(List.of("java", "sql"), DumJsonPath.compile("skills.name").read(JOB));
        assertEquals(List.of("a", "b", "c"), DumJsonPath.compile("skills.tags").read(JOB));
    }

    @Test
    void rootAndArraysReadAsThemselves() {
        assertSame(JOB, DumJsonPath.compile("$").read(JOB));
        assertSame(JOB, DumJsonPath.compile("").read(JOB));
        assertTrue(DumJsonPath.compile("data.listVagas").read(JOB) instanceof JSONArray);
    }

    @Test
    void jsonNullAndMissingKeysAreAbsent() {
        assertNull(DumJsonPath.compile("salary").read(JOB));
        assertNull(DumJsonPath.compile("nope.deeper").read(JOB));
        assertNull(DumJsonPath.compile("title.deeper").read(JOB));
    }

    @Test
    void refusesAMalformedPath() {
        assertThrows(IllegalArgumentException.class, () -> DumJsonPath.compile("a..b"));
        assertThrows(IllegalArgumentException.class, () -> DumJsonPath.compile("a[x]"));
    }
}
