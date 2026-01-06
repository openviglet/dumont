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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DumAemSourceAttribute Tests")
class DumAemSourceAttributeTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build source attribute with all fields")
        void shouldBuildSourceAttributeWithAllFields() {
            DumAemTargetAttribute targetAttr = DumAemTargetAttribute.builder()
                    .id("target-id")
                    .name("title")
                    .build();

            DumAemSourceAttribute sourceAttr = DumAemSourceAttribute.builder()
                    .id("source-attr-id")
                    .name("jcr:title")
                    .className("com.example.TitleHandler")
                    .text("Static Text")
                    .dumAemTargetAttribute(targetAttr)
                    .build();

            assertEquals("source-attr-id", sourceAttr.getId());
            assertEquals("jcr:title", sourceAttr.getName());
            assertEquals("com.example.TitleHandler", sourceAttr.getClassName());
            assertEquals("Static Text", sourceAttr.getText());
            assertNotNull(sourceAttr.getDumAemTargetAttribute());
        }

        @Test
        @DisplayName("Should build source attribute with minimal fields")
        void shouldBuildSourceAttributeWithMinimalFields() {
            DumAemSourceAttribute sourceAttr = DumAemSourceAttribute.builder()
                    .name("jcr:description")
                    .build();

            assertEquals("jcr:description", sourceAttr.getName());
            assertNull(sourceAttr.getId());
            assertNull(sourceAttr.getClassName());
            assertNull(sourceAttr.getText());
        }
    }

    @Nested
    @DisplayName("NoArgs Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create source attribute with default values")
        void shouldCreateSourceAttributeWithDefaultValues() {
            DumAemSourceAttribute sourceAttr = new DumAemSourceAttribute();

            assertNull(sourceAttr.getId());
            assertNull(sourceAttr.getName());
            assertNull(sourceAttr.getClassName());
            assertNull(sourceAttr.getText());
            assertNull(sourceAttr.getDumAemTargetAttribute());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get id")
        void shouldSetAndGetId() {
            DumAemSourceAttribute sourceAttr = new DumAemSourceAttribute();
            sourceAttr.setId("new-id");

            assertEquals("new-id", sourceAttr.getId());
        }

        @Test
        @DisplayName("Should set and get name")
        void shouldSetAndGetName() {
            DumAemSourceAttribute sourceAttr = new DumAemSourceAttribute();
            sourceAttr.setName("newName");

            assertEquals("newName", sourceAttr.getName());
        }

        @Test
        @DisplayName("Should set and get className")
        void shouldSetAndGetClassName() {
            DumAemSourceAttribute sourceAttr = new DumAemSourceAttribute();
            sourceAttr.setClassName("com.example.Handler");

            assertEquals("com.example.Handler", sourceAttr.getClassName());
        }

        @Test
        @DisplayName("Should set and get text")
        void shouldSetAndGetText() {
            DumAemSourceAttribute sourceAttr = new DumAemSourceAttribute();
            sourceAttr.setText("New Text");

            assertEquals("New Text", sourceAttr.getText());
        }

        @Test
        @DisplayName("Should set and get target attribute")
        void shouldSetAndGetTargetAttribute() {
            DumAemSourceAttribute sourceAttr = new DumAemSourceAttribute();
            DumAemTargetAttribute targetAttr = DumAemTargetAttribute.builder().name("test").build();
            sourceAttr.setDumAemTargetAttribute(targetAttr);

            assertEquals(targetAttr, sourceAttr.getDumAemTargetAttribute());
        }
    }

    @Nested
    @DisplayName("toBuilder Tests")
    class ToBuilderTests {

        @Test
        @DisplayName("Should create builder from existing source attribute")
        void shouldCreateBuilderFromExistingSourceAttribute() {
            DumAemSourceAttribute original = DumAemSourceAttribute.builder()
                    .id("original-id")
                    .name("originalName")
                    .className("com.example.Original")
                    .build();

            DumAemSourceAttribute copy = original.toBuilder()
                    .name("modifiedName")
                    .build();

            assertEquals("original-id", copy.getId());
            assertEquals("modifiedName", copy.getName());
            assertEquals("com.example.Original", copy.getClassName());
        }
    }

    @Nested
    @DisplayName("Serializable Tests")
    class SerializableTests {

        @Test
        @DisplayName("Should be serializable")
        void shouldBeSerializable() {
            DumAemSourceAttribute sourceAttr = DumAemSourceAttribute.builder()
                    .name("test")
                    .build();

            assertTrue(sourceAttr instanceof java.io.Serializable);
        }
    }
}
