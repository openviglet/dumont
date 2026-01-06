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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DumAemSourceAttrExchange Tests")
class DumAemSourceAttrExchangeTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create instance with builder")
        void shouldCreateInstanceWithBuilder() {
            DumAemSourceAttrExchange exchange = DumAemSourceAttrExchange.builder()
                    .name("sourceAttrName")
                    .className("com.example.SourceClass")
                    .text("Source Attribute Text")
                    .build();

            assertEquals("sourceAttrName", exchange.getName());
            assertEquals("com.example.SourceClass", exchange.getClassName());
            assertEquals("Source Attribute Text", exchange.getText());
        }

        @Test
        @DisplayName("Should support toBuilder")
        void shouldSupportToBuilder() {
            DumAemSourceAttrExchange original = DumAemSourceAttrExchange.builder()
                    .name("original")
                    .className("com.example.Original")
                    .build();

            DumAemSourceAttrExchange modified = original.toBuilder()
                    .name("modified")
                    .build();

            assertEquals("modified", modified.getName());
            assertEquals("com.example.Original", modified.getClassName());
        }
    }

    @Nested
    @DisplayName("No Args Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create instance with no args constructor")
        void shouldCreateInstanceWithNoArgsConstructor() {
            DumAemSourceAttrExchange exchange = new DumAemSourceAttrExchange();

            assertNotNull(exchange);
            assertNull(exchange.getName());
            assertNull(exchange.getClassName());
            assertNull(exchange.getText());
        }
    }

    @Nested
    @DisplayName("All Args Constructor Tests")
    class AllArgsConstructorTests {

        @Test
        @DisplayName("Should create instance with all args constructor")
        void shouldCreateInstanceWithAllArgsConstructor() {
            DumAemSourceAttrExchange exchange = new DumAemSourceAttrExchange(
                    "attrName", "com.example.Class", "Attr Text");

            assertEquals("attrName", exchange.getName());
            assertEquals("com.example.Class", exchange.getClassName());
            assertEquals("Attr Text", exchange.getText());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get name")
        void shouldSetAndGetName() {
            DumAemSourceAttrExchange exchange = new DumAemSourceAttrExchange();
            exchange.setName("testName");

            assertEquals("testName", exchange.getName());
        }

        @Test
        @DisplayName("Should set and get className")
        void shouldSetAndGetClassName() {
            DumAemSourceAttrExchange exchange = new DumAemSourceAttrExchange();
            exchange.setClassName("com.example.MyClass");

            assertEquals("com.example.MyClass", exchange.getClassName());
        }

        @Test
        @DisplayName("Should set and get text")
        void shouldSetAndGetText() {
            DumAemSourceAttrExchange exchange = new DumAemSourceAttrExchange();
            exchange.setText("Test text");

            assertEquals("Test text", exchange.getText());
        }
    }
}
