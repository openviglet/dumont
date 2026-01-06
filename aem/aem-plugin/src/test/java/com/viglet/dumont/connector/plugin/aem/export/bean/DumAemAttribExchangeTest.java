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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.field.TurSEFieldType;

@DisplayName("DumAemAttribExchange Tests")
class DumAemAttribExchangeTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create instance with builder")
        void shouldCreateInstanceWithBuilder() {
            DumAemAttribExchange exchange = DumAemAttribExchange.builder()
                    .name("attributeName")
                    .className("com.example.AttributeClass")
                    .text("Attribute Text")
                    .type(TurSEFieldType.STRING)
                    .mandatory(true)
                    .multiValued(false)
                    .description("Attribute description")
                    .facet(true)
                    .build();

            assertEquals("attributeName", exchange.getName());
            assertEquals("com.example.AttributeClass", exchange.getClassName());
            assertEquals("Attribute Text", exchange.getText());
            assertEquals(TurSEFieldType.STRING, exchange.getType());
            assertTrue(exchange.isMandatory());
            assertFalse(exchange.isMultiValued());
            assertEquals("Attribute description", exchange.getDescription());
            assertTrue(exchange.isFacet());
        }

        @Test
        @DisplayName("Should initialize facetName with empty HashMap by default")
        void shouldInitializeFacetNameWithEmptyHashMapByDefault() {
            DumAemAttribExchange exchange = DumAemAttribExchange.builder().build();

            assertNotNull(exchange.getFacetName());
            assertTrue(exchange.getFacetName().isEmpty());
        }

        @Test
        @DisplayName("Should create instance with facetName")
        void shouldCreateInstanceWithFacetName() {
            Map<String, String> facetNames = new HashMap<>();
            facetNames.put("en", "English");
            facetNames.put("pt", "Portuguese");

            DumAemAttribExchange exchange = DumAemAttribExchange.builder()
                    .facetName(facetNames)
                    .build();

            assertEquals(2, exchange.getFacetName().size());
            assertEquals("English", exchange.getFacetName().get("en"));
        }

        @Test
        @DisplayName("Should support toBuilder")
        void shouldSupportToBuilder() {
            DumAemAttribExchange original = DumAemAttribExchange.builder()
                    .name("originalName")
                    .type(TurSEFieldType.STRING)
                    .build();

            DumAemAttribExchange modified = original.toBuilder()
                    .type(TurSEFieldType.DATE)
                    .build();

            assertEquals("originalName", modified.getName());
            assertEquals(TurSEFieldType.DATE, modified.getType());
        }
    }

    @Nested
    @DisplayName("No Args Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create instance with no args constructor")
        void shouldCreateInstanceWithNoArgsConstructor() {
            DumAemAttribExchange exchange = new DumAemAttribExchange();

            assertNotNull(exchange);
            assertNull(exchange.getName());
            assertNull(exchange.getClassName());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get name")
        void shouldSetAndGetName() {
            DumAemAttribExchange exchange = new DumAemAttribExchange();
            exchange.setName("testName");

            assertEquals("testName", exchange.getName());
        }

        @Test
        @DisplayName("Should set and get className")
        void shouldSetAndGetClassName() {
            DumAemAttribExchange exchange = new DumAemAttribExchange();
            exchange.setClassName("com.example.Class");

            assertEquals("com.example.Class", exchange.getClassName());
        }

        @Test
        @DisplayName("Should set and get text")
        void shouldSetAndGetText() {
            DumAemAttribExchange exchange = new DumAemAttribExchange();
            exchange.setText("Test Text");

            assertEquals("Test Text", exchange.getText());
        }

        @Test
        @DisplayName("Should set and get type")
        void shouldSetAndGetType() {
            DumAemAttribExchange exchange = new DumAemAttribExchange();
            exchange.setType(TurSEFieldType.INT);

            assertEquals(TurSEFieldType.INT, exchange.getType());
        }

        @Test
        @DisplayName("Should set and get mandatory")
        void shouldSetAndGetMandatory() {
            DumAemAttribExchange exchange = new DumAemAttribExchange();
            exchange.setMandatory(true);

            assertTrue(exchange.isMandatory());
        }

        @Test
        @DisplayName("Should set and get multiValued")
        void shouldSetAndGetMultiValued() {
            DumAemAttribExchange exchange = new DumAemAttribExchange();
            exchange.setMultiValued(true);

            assertTrue(exchange.isMultiValued());
        }

        @Test
        @DisplayName("Should set and get description")
        void shouldSetAndGetDescription() {
            DumAemAttribExchange exchange = new DumAemAttribExchange();
            exchange.setDescription("Test description");

            assertEquals("Test description", exchange.getDescription());
        }

        @Test
        @DisplayName("Should set and get facet")
        void shouldSetAndGetFacet() {
            DumAemAttribExchange exchange = new DumAemAttribExchange();
            exchange.setFacet(true);

            assertTrue(exchange.isFacet());
        }

        @Test
        @DisplayName("Should set and get facetName")
        void shouldSetAndGetFacetName() {
            DumAemAttribExchange exchange = new DumAemAttribExchange();
            Map<String, String> facetNames = new HashMap<>();
            facetNames.put("es", "Spanish");
            exchange.setFacetName(facetNames);

            assertEquals(1, exchange.getFacetName().size());
            assertEquals("Spanish", exchange.getFacetName().get("es"));
        }
    }
}
