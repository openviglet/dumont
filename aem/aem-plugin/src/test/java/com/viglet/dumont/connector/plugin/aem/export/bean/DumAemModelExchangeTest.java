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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DumAemModelExchange Tests")
class DumAemModelExchangeTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create instance with builder")
        void shouldCreateInstanceWithBuilder() {
            DumAemModelExchange exchange = DumAemModelExchange.builder()
                    .type("cq:Page")
                    .className("com.example.PageModel")
                    .build();

            assertEquals("cq:Page", exchange.getType());
            assertEquals("com.example.PageModel", exchange.getClassName());
        }

        @Test
        @DisplayName("Should initialize targetAttrs with empty HashSet by default")
        void shouldInitializeTargetAttrsWithEmptyHashSetByDefault() {
            DumAemModelExchange exchange = DumAemModelExchange.builder().build();

            assertNotNull(exchange.getTargetAttrs());
            assertTrue(exchange.getTargetAttrs().isEmpty());
        }

        @Test
        @DisplayName("Should support toBuilder")
        void shouldSupportToBuilder() {
            DumAemModelExchange original = DumAemModelExchange.builder()
                    .type("cq:Page")
                    .className("com.example.PageModel")
                    .build();

            DumAemModelExchange modified = original.toBuilder()
                    .type("dam:Asset")
                    .build();

            assertEquals("dam:Asset", modified.getType());
            assertEquals("com.example.PageModel", modified.getClassName());
        }
    }

    @Nested
    @DisplayName("No Args Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create instance with no args constructor")
        void shouldCreateInstanceWithNoArgsConstructor() {
            DumAemModelExchange exchange = new DumAemModelExchange();

            assertNotNull(exchange);
            assertNull(exchange.getType());
            assertNull(exchange.getClassName());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get type")
        void shouldSetAndGetType() {
            DumAemModelExchange exchange = new DumAemModelExchange();
            exchange.setType("nt:unstructured");

            assertEquals("nt:unstructured", exchange.getType());
        }

        @Test
        @DisplayName("Should set and get className")
        void shouldSetAndGetClassName() {
            DumAemModelExchange exchange = new DumAemModelExchange();
            exchange.setClassName("com.example.MyModel");

            assertEquals("com.example.MyModel", exchange.getClassName());
        }

        @Test
        @DisplayName("Should set and get targetAttrs")
        void shouldSetAndGetTargetAttrs() {
            DumAemModelExchange exchange = new DumAemModelExchange();
            HashSet<DumAemTargetAttrExchange> targetAttrs = new HashSet<>();
            targetAttrs.add(DumAemTargetAttrExchange.builder().name("attr1").build());
            targetAttrs.add(DumAemTargetAttrExchange.builder().name("attr2").build());
            exchange.setTargetAttrs(targetAttrs);

            assertEquals(2, exchange.getTargetAttrs().size());
        }
    }
}
