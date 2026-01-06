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

package com.viglet.dumont.connector.plugin.aem.export.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DumAemTargetAttrExchange Tests")
class DumAemTargetAttrExchangeTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create instance with builder")
        void shouldCreateInstanceWithBuilder() {
            DumAemTargetAttrExchange exchange = DumAemTargetAttrExchange.builder()
                    .name("targetAttrName")
                    .build();

            assertEquals("targetAttrName", exchange.getName());
        }

        @Test
        @DisplayName("Should initialize sourceAttrs with empty HashSet by default")
        void shouldInitializeSourceAttrsWithEmptyHashSetByDefault() {
            DumAemTargetAttrExchange exchange = DumAemTargetAttrExchange.builder().build();

            assertNotNull(exchange.getSourceAttrs());
            assertTrue(exchange.getSourceAttrs().isEmpty());
        }

        @Test
        @DisplayName("Should support toBuilder")
        void shouldSupportToBuilder() {
            DumAemTargetAttrExchange original = DumAemTargetAttrExchange.builder()
                    .name("original")
                    .build();

            DumAemTargetAttrExchange modified = original.toBuilder()
                    .name("modified")
                    .build();

            assertEquals("modified", modified.getName());
        }
    }

    @Nested
    @DisplayName("No Args Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create instance with no args constructor")
        void shouldCreateInstanceWithNoArgsConstructor() {
            DumAemTargetAttrExchange exchange = new DumAemTargetAttrExchange();

            assertNotNull(exchange);
            assertNull(exchange.getName());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get name")
        void shouldSetAndGetName() {
            DumAemTargetAttrExchange exchange = new DumAemTargetAttrExchange();
            exchange.setName("testName");

            assertEquals("testName", exchange.getName());
        }

        @Test
        @DisplayName("Should set and get sourceAttrs")
        void shouldSetAndGetSourceAttrs() {
            DumAemTargetAttrExchange exchange = new DumAemTargetAttrExchange();
            HashSet<DumAemSourceAttrExchange> sourceAttrs = new HashSet<>();
            sourceAttrs.add(DumAemSourceAttrExchange.builder().name("source1").build());
            sourceAttrs.add(DumAemSourceAttrExchange.builder().name("source2").build());
            exchange.setSourceAttrs(sourceAttrs);

            assertEquals(2, exchange.getSourceAttrs().size());
        }
    }
}
