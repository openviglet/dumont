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

import java.util.HashSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DumAemTargetAttribute Tests")
class DumAemTargetAttributeTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build target attribute with all fields")
        void shouldBuildTargetAttributeWithAllFields() {
            DumAemPluginModel model = DumAemPluginModel.builder()
                    .id("model-id")
                    .type("cq:Page")
                    .build();

            DumAemTargetAttribute targetAttr = DumAemTargetAttribute.builder()
                    .id("attr-id")
                    .name("title")
                    .dumAemModel(model)
                    .build();

            assertEquals("attr-id", targetAttr.getId());
            assertEquals("title", targetAttr.getName());
            assertNotNull(targetAttr.getDumAemModel());
            assertNotNull(targetAttr.getSourceAttrs());
        }

        @Test
        @DisplayName("Should build target attribute with minimal fields")
        void shouldBuildTargetAttributeWithMinimalFields() {
            DumAemTargetAttribute targetAttr = DumAemTargetAttribute.builder()
                    .name("description")
                    .build();

            assertEquals("description", targetAttr.getName());
            assertNull(targetAttr.getId());
            assertNotNull(targetAttr.getSourceAttrs());
            assertTrue(targetAttr.getSourceAttrs().isEmpty());
        }

        @Test
        @DisplayName("Should initialize sourceAttrs with empty HashSet")
        void shouldInitializeSourceAttrsWithEmptyHashSet() {
            DumAemTargetAttribute targetAttr = DumAemTargetAttribute.builder().build();

            assertNotNull(targetAttr.getSourceAttrs());
            assertTrue(targetAttr.getSourceAttrs().isEmpty());
        }
    }

    @Nested
    @DisplayName("NoArgs Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create target attribute with default values")
        void shouldCreateTargetAttributeWithDefaultValues() {
            DumAemTargetAttribute targetAttr = new DumAemTargetAttribute();

            assertNull(targetAttr.getId());
            assertNull(targetAttr.getName());
            assertNull(targetAttr.getDumAemModel());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get id")
        void shouldSetAndGetId() {
            DumAemTargetAttribute targetAttr = new DumAemTargetAttribute();
            targetAttr.setId("new-id");

            assertEquals("new-id", targetAttr.getId());
        }

        @Test
        @DisplayName("Should set and get name")
        void shouldSetAndGetName() {
            DumAemTargetAttribute targetAttr = new DumAemTargetAttribute();
            targetAttr.setName("newName");

            assertEquals("newName", targetAttr.getName());
        }

        @Test
        @DisplayName("Should set and get sourceAttrs")
        void shouldSetAndGetSourceAttrs() {
            DumAemTargetAttribute targetAttr = new DumAemTargetAttribute();
            targetAttr.setSourceAttrs(new HashSet<>());

            assertNotNull(targetAttr.getSourceAttrs());
        }

        @Test
        @DisplayName("Should set and get model")
        void shouldSetAndGetModel() {
            DumAemTargetAttribute targetAttr = new DumAemTargetAttribute();
            DumAemPluginModel model = DumAemPluginModel.builder().type("cq:Page").build();
            targetAttr.setDumAemModel(model);

            assertEquals(model, targetAttr.getDumAemModel());
        }
    }

    @Nested
    @DisplayName("toBuilder Tests")
    class ToBuilderTests {

        @Test
        @DisplayName("Should create builder from existing target attribute")
        void shouldCreateBuilderFromExistingTargetAttribute() {
            DumAemTargetAttribute original = DumAemTargetAttribute.builder()
                    .id("original-id")
                    .name("originalName")
                    .build();

            DumAemTargetAttribute copy = original.toBuilder()
                    .name("modifiedName")
                    .build();

            assertEquals("original-id", copy.getId());
            assertEquals("modifiedName", copy.getName());
        }
    }

    @Nested
    @DisplayName("Serializable Tests")
    class SerializableTests {

        @Test
        @DisplayName("Should be serializable")
        void shouldBeSerializable() {
            DumAemTargetAttribute targetAttr = DumAemTargetAttribute.builder()
                    .name("test")
                    .build();

            assertTrue(targetAttr instanceof java.io.Serializable);
        }
    }
}
