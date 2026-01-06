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

@DisplayName("DumAemPluginModel Tests")
class DumAemPluginModelTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build plugin model with all fields")
        void shouldBuildPluginModelWithAllFields() {
            DumAemSource source = DumAemSource.builder()
                    .id("source-id")
                    .name("test-source")
                    .build();

            DumAemPluginModel model = DumAemPluginModel.builder()
                    .id("model-id")
                    .type("cq:Page")
                    .subType("article")
                    .className("com.example.ArticleModel")
                    .dumAemSource(source)
                    .build();

            assertEquals("model-id", model.getId());
            assertEquals("cq:Page", model.getType());
            assertEquals("article", model.getSubType());
            assertEquals("com.example.ArticleModel", model.getClassName());
            assertNotNull(model.getDumAemSource());
            assertNotNull(model.getTargetAttrs());
        }

        @Test
        @DisplayName("Should build plugin model with minimal fields")
        void shouldBuildPluginModelWithMinimalFields() {
            DumAemPluginModel model = DumAemPluginModel.builder()
                    .type("cq:Page")
                    .build();

            assertEquals("cq:Page", model.getType());
            assertNull(model.getId());
            assertNull(model.getSubType());
            assertNull(model.getClassName());
            assertNotNull(model.getTargetAttrs());
            assertTrue(model.getTargetAttrs().isEmpty());
        }

        @Test
        @DisplayName("Should initialize targetAttrs with empty HashSet")
        void shouldInitializeTargetAttrsWithEmptyHashSet() {
            DumAemPluginModel model = DumAemPluginModel.builder().build();

            assertNotNull(model.getTargetAttrs());
            assertTrue(model.getTargetAttrs().isEmpty());
        }
    }

    @Nested
    @DisplayName("NoArgs Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create plugin model with default values")
        void shouldCreatePluginModelWithDefaultValues() {
            DumAemPluginModel model = new DumAemPluginModel();

            assertNull(model.getId());
            assertNull(model.getType());
            assertNull(model.getSubType());
            assertNull(model.getClassName());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get id")
        void shouldSetAndGetId() {
            DumAemPluginModel model = new DumAemPluginModel();
            model.setId("new-id");

            assertEquals("new-id", model.getId());
        }

        @Test
        @DisplayName("Should set and get type")
        void shouldSetAndGetType() {
            DumAemPluginModel model = new DumAemPluginModel();
            model.setType("dam:Asset");

            assertEquals("dam:Asset", model.getType());
        }

        @Test
        @DisplayName("Should set and get subType")
        void shouldSetAndGetSubType() {
            DumAemPluginModel model = new DumAemPluginModel();
            model.setSubType("image");

            assertEquals("image", model.getSubType());
        }

        @Test
        @DisplayName("Should set and get className")
        void shouldSetAndGetClassName() {
            DumAemPluginModel model = new DumAemPluginModel();
            model.setClassName("com.example.NewModel");

            assertEquals("com.example.NewModel", model.getClassName());
        }

        @Test
        @DisplayName("Should set and get targetAttrs")
        void shouldSetAndGetTargetAttrs() {
            DumAemPluginModel model = new DumAemPluginModel();
            model.setTargetAttrs(new HashSet<>());

            assertNotNull(model.getTargetAttrs());
        }

        @Test
        @DisplayName("Should set and get source")
        void shouldSetAndGetSource() {
            DumAemPluginModel model = new DumAemPluginModel();
            DumAemSource source = DumAemSource.builder().name("test").build();
            model.setDumAemSource(source);

            assertEquals(source, model.getDumAemSource());
        }
    }

    @Nested
    @DisplayName("toBuilder Tests")
    class ToBuilderTests {

        @Test
        @DisplayName("Should create builder from existing model")
        void shouldCreateBuilderFromExistingModel() {
            DumAemPluginModel original = DumAemPluginModel.builder()
                    .id("original-id")
                    .type("cq:Page")
                    .subType("page")
                    .build();

            DumAemPluginModel copy = original.toBuilder()
                    .subType("article")
                    .build();

            assertEquals("original-id", copy.getId());
            assertEquals("cq:Page", copy.getType());
            assertEquals("article", copy.getSubType());
        }
    }

    @Nested
    @DisplayName("Serializable Tests")
    class SerializableTests {

        @Test
        @DisplayName("Should be serializable")
        void shouldBeSerializable() {
            DumAemPluginModel model = DumAemPluginModel.builder()
                    .type("cq:Page")
                    .build();

            assertTrue(model instanceof java.io.Serializable);
        }
    }
}
