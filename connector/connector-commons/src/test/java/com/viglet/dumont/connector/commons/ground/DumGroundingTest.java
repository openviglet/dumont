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

package com.viglet.dumont.connector.commons.ground;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Unit test for the reusable grounding contract (roadmap D03). */
class DumGroundingTest {

    @Test
    void isBlankCoversNullBlankTextAndAllBlankCollections() {
        assertTrue(DumGrounding.isBlank(null));
        assertTrue(DumGrounding.isBlank(""));
        assertTrue(DumGrounding.isBlank("   "));
        assertTrue(DumGrounding.isBlank(List.of()));
        assertTrue(DumGrounding.isBlank(Arrays.asList("", "  ")));

        assertFalse(DumGrounding.isBlank("x"));
        assertFalse(DumGrounding.isBlank(0));
        assertFalse(DumGrounding.isBlank(false));
        assertFalse(DumGrounding.isBlank(List.of("a")));
    }

    @Test
    void groundDropsBlanksAndCleansCollections() {
        assertNull(DumGrounding.ground(""));
        assertNull(DumGrounding.ground("  "));
        assertNull(DumGrounding.ground(null));
        assertNull(DumGrounding.ground(Arrays.asList("", null, "  ")));

        assertEquals("v", DumGrounding.ground("v"));
        assertEquals(List.of("a", "b"), DumGrounding.ground(Arrays.asList("a", "", "b", null)));
    }

    @Test
    void groundAttributesOmitsAbsentFieldsAndKeepsRealValues() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("title", "Algorithms");
        attributes.put("description", ""); // blank -> omitted, not ""
        attributes.put("area", Arrays.asList("CS", "", null)); // cleaned
        attributes.put("tags", List.of()); // empty -> omitted
        attributes.put("credits", 4);

        Map<String, Object> grounded = DumGrounding.groundAttributes(attributes);

        assertEquals("Algorithms", grounded.get("title"));
        assertEquals(List.of("CS"), grounded.get("area"));
        assertEquals(4, grounded.get("credits"));
        assertFalse(grounded.containsKey("description"),
                "blank field must be omitted, not published as empty string");
        assertFalse(grounded.containsKey("tags"), "empty collection must be omitted");
        assertEquals(3, grounded.size());
    }

    @Test
    void groundAttributesReturnsNullAndEmptyInputUnchanged() {
        assertNull(DumGrounding.groundAttributes(null));
        Map<String, Object> empty = Map.of();
        assertSame(empty, DumGrounding.groundAttributes(empty));
    }
}
