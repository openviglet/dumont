/*
 * Copyright (C) 2016-2025 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.aem.commons.ext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.bean.DumAemAttrMap;

/**
 * Verifies that {@link DumAemExtModelJsonBase} composes its registered
 * {@link DumAemComponentExtractor}s — the core of the D24 component-extractor
 * SPI — running each in registration order and accumulating into one map.
 */
@DisplayName("DumAemExtModelJsonBase component-extractor composition")
class DumAemComponentExtractorCompositionTest {

    /** A base whose extraction is expressed purely as registered component extractors. */
    private static class ComposedExtractor extends DumAemExtModelJsonBase<String> {
        private final List<DumAemComponentExtractor<String>> extractors;

        ComposedExtractor(List<DumAemComponentExtractor<String>> extractors) {
            this.extractors = extractors;
        }

        @Override
        protected Class<String> getModelClass() {
            return String.class;
        }

        @Override
        protected List<DumAemComponentExtractor<String>> componentExtractors() {
            return extractors;
        }

        DumAemAttrMap run(String model) {
            DumAemAttrMap attrs = new DumAemAttrMap();
            extractAttributes(model, null, null, attrs);
            return attrs;
        }
    }

    @Test
    @DisplayName("Should run every registered extractor, each contributing its own attribute")
    void shouldComposeAllExtractors() {
        List<String> order = new ArrayList<>();
        DumAemComponentExtractor<String> a = (m, q, o, attrs) -> {
            order.add("a");
            attrs.set("first", m + "-a");
        };
        DumAemComponentExtractor<String> b = (m, q, o, attrs) -> {
            order.add("b");
            attrs.set("second", m + "-b");
        };

        DumAemAttrMap attrs = new ComposedExtractor(List.of(a, b)).run("x");

        assertEquals(List.of("a", "b"), order);
        assertEquals("x-a", attrs.get("first").getFirst());
        assertEquals("x-b", attrs.get("second").getFirst());
    }

    @Test
    @DisplayName("Should contribute nothing when no extractors are registered")
    void shouldDoNothingWithoutExtractors() {
        DumAemAttrMap attrs = new ComposedExtractor(List.of()).run("x");
        assertTrue(attrs.isEmpty());
    }

    @Test
    @DisplayName("Should let later extractors append to a shared attribute")
    void shouldShareAttributeMap() {
        DumAemComponentExtractor<String> first = (m, q, o, attrs) -> attrs.append("tags", "one");
        DumAemComponentExtractor<String> second = (m, q, o, attrs) -> attrs.append("tags", "two");

        DumAemAttrMap attrs = new ComposedExtractor(List.of(first, second)).run("x");

        assertEquals(List.of("one", "two"), attrs.get("tags"));
    }
}
