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

package com.viglet.dumont.connector.aem.commons.ext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.bean.DumAemAttrMap;

class DumAemComponentMapperTest {

    @Test
    void allAppliesMappingForEveryItem() {
        DumAemAttrMap attrMap = new DumAemAttrMap();

        new DumAemComponentMapper<>(List.of("alpha", "beta"))
                .all()
                .attr("name", s -> s)
                .into(attrMap);

        assertTrue(attrMap.containsKey("name"));
    }

    @Test
    void firstAppliesMappingForFirstItemOnly() {
        DumAemAttrMap attrMap = new DumAemAttrMap();

        new DumAemComponentMapper<>(List.of("alpha", "beta"))
                .first()
                .attr("name", s -> s, false)
                .into(attrMap);

        assertTrue(attrMap.containsKey("name"));
    }

    @Test
    void alsoRunsCustomAction() {
        DumAemAttrMap attrMap = new DumAemAttrMap();

        new DumAemComponentMapper<>(List.of("alpha"))
                .also((item, map) -> map.set("custom", item))
                .into(attrMap);

        assertTrue(attrMap.containsKey("custom"));
    }

    @Test
    void viaNavigatesToAnotherTypeBeforeMapping() {
        DumAemAttrMap attrMap = new DumAemAttrMap();

        new DumAemComponentMapper<>(List.of("alpha"))
                .via(String::toUpperCase)
                .attr("upper", s -> s)
                .into(attrMap);

        assertTrue(attrMap.containsKey("upper"));
    }

    @Test
    void nullItemsAreSkippedWithoutError() {
        DumAemAttrMap attrMap = new DumAemAttrMap();
        List<String> withNull = new ArrayList<>();
        withNull.add("alpha");
        withNull.add(null);

        assertDoesNotThrow(() -> new DumAemComponentMapper<>(withNull)
                .all()
                .attr("name", s -> s)
                .into(attrMap));
    }
}
