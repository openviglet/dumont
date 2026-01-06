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

package com.viglet.dumont.connector.plugin.aem.persistence.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.field.TurSEFieldType;

@DisplayName("DumAemAttributeSpecification Tests")
class DumAemAttributeSpecificationTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create instance with builder")
        void shouldCreateInstanceWithBuilder() {
            DumAemAttributeSpecification spec = DumAemAttributeSpecification.builder()
                    .id("spec-id")
                    .name("attributeName")
                    .text("Attribute Text")
                    .className("com.example.AttributeClass")
                    .type(TurSEFieldType.STRING)
                    .mandatory(true)
                    .multiValued(false)
                    .description("Attribute description")
                    .facet(true)
                    .build();

            assertEquals("spec-id", spec.getId());
            assertEquals("attributeName", spec.getName());
            assertEquals("Attribute Text", spec.getText());
            assertEquals("com.example.AttributeClass", spec.getClassName());
            assertEquals(TurSEFieldType.STRING, spec.getType());
            assertTrue(spec.isMandatory());
            assertFalse(spec.isMultiValued());
            assertEquals("Attribute description", spec.getDescription());
            assertTrue(spec.isFacet());
        }

        @Test
        @DisplayName("Should initialize facetNames with empty map by default")
        void shouldInitializeFacetNamesWithEmptyMapByDefault() {
            DumAemAttributeSpecification spec = DumAemAttributeSpecification.builder().build();

            assertNotNull(spec.getFacetNames());
            assertTrue(spec.getFacetNames().isEmpty());
        }

        @Test
        @DisplayName("Should create instance with facetNames")
        void shouldCreateInstanceWithFacetNames() {
            Map<String, String> facetNames = new HashMap<>();
            facetNames.put("en", "English Name");
            facetNames.put("pt", "Portuguese Name");

            DumAemAttributeSpecification spec = DumAemAttributeSpecification.builder()
                    .facetNames(facetNames)
                    .build();

            assertEquals(2, spec.getFacetNames().size());
            assertEquals("English Name", spec.getFacetNames().get("en"));
            assertEquals("Portuguese Name", spec.getFacetNames().get("pt"));
        }
    }

    @Nested
    @DisplayName("No Args Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create instance with no args constructor")
        void shouldCreateInstanceWithNoArgsConstructor() {
            DumAemAttributeSpecification spec = new DumAemAttributeSpecification();

            assertNotNull(spec);
            assertNull(spec.getId());
            assertNull(spec.getName());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get id")
        void shouldSetAndGetId() {
            DumAemAttributeSpecification spec = new DumAemAttributeSpecification();
            spec.setId("test-id");

            assertEquals("test-id", spec.getId());
        }

        @Test
        @DisplayName("Should set and get className")
        void shouldSetAndGetClassName() {
            DumAemAttributeSpecification spec = new DumAemAttributeSpecification();
            spec.setClassName("com.example.MyClass");

            assertEquals("com.example.MyClass", spec.getClassName());
        }

        @Test
        @DisplayName("Should set and get text")
        void shouldSetAndGetText() {
            DumAemAttributeSpecification spec = new DumAemAttributeSpecification();
            spec.setText("My Text");

            assertEquals("My Text", spec.getText());
        }

        @Test
        @DisplayName("Should set and get name")
        void shouldSetAndGetName() {
            DumAemAttributeSpecification spec = new DumAemAttributeSpecification();
            spec.setName("attrName");

            assertEquals("attrName", spec.getName());
        }

        @Test
        @DisplayName("Should set and get type")
        void shouldSetAndGetType() {
            DumAemAttributeSpecification spec = new DumAemAttributeSpecification();
            spec.setType(TurSEFieldType.DATE);

            assertEquals(TurSEFieldType.DATE, spec.getType());
        }

        @Test
        @DisplayName("Should set and get mandatory")
        void shouldSetAndGetMandatory() {
            DumAemAttributeSpecification spec = new DumAemAttributeSpecification();
            spec.setMandatory(true);

            assertTrue(spec.isMandatory());
        }

        @Test
        @DisplayName("Should set and get multiValued")
        void shouldSetAndGetMultiValued() {
            DumAemAttributeSpecification spec = new DumAemAttributeSpecification();
            spec.setMultiValued(true);

            assertTrue(spec.isMultiValued());
        }

        @Test
        @DisplayName("Should set and get description")
        void shouldSetAndGetDescription() {
            DumAemAttributeSpecification spec = new DumAemAttributeSpecification();
            spec.setDescription("Test description");

            assertEquals("Test description", spec.getDescription());
        }

        @Test
        @DisplayName("Should set and get facet")
        void shouldSetAndGetFacet() {
            DumAemAttributeSpecification spec = new DumAemAttributeSpecification();
            spec.setFacet(true);

            assertTrue(spec.isFacet());
        }

        @Test
        @DisplayName("Should set and get facetNames")
        void shouldSetAndGetFacetNames() {
            DumAemAttributeSpecification spec = new DumAemAttributeSpecification();
            Map<String, String> facetNames = new HashMap<>();
            facetNames.put("es", "Spanish");
            spec.setFacetNames(facetNames);

            assertEquals(1, spec.getFacetNames().size());
            assertEquals("Spanish", spec.getFacetNames().get("es"));
        }

        @Test
        @DisplayName("Should set and get dumAemSource")
        void shouldSetAndGetDumAemSource() {
            DumAemAttributeSpecification spec = new DumAemAttributeSpecification();
            DumAemSource source = new DumAemSource();
            source.setId("source-id");
            spec.setDumAemSource(source);

            assertEquals("source-id", spec.getDumAemSource().getId());
        }
    }

    @Nested
    @DisplayName("Serializable Tests")
    class SerializableTests {

        @Test
        @DisplayName("Should be serializable")
        void shouldBeSerializable() throws Exception {
            DumAemAttributeSpecification spec = DumAemAttributeSpecification.builder()
                    .id("spec-id")
                    .name("testAttr")
                    .type(TurSEFieldType.STRING)
                    .mandatory(true)
                    .build();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(spec);
            oos.close();

            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bis);
            DumAemAttributeSpecification deserialized = (DumAemAttributeSpecification) ois.readObject();

            assertEquals(spec.getId(), deserialized.getId());
            assertEquals(spec.getName(), deserialized.getName());
            assertEquals(spec.getType(), deserialized.getType());
            assertEquals(spec.isMandatory(), deserialized.isMandatory());
        }
    }
}
