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

package com.viglet.dumont.connector.aem.commons.mappers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DumAemModel Tests")
class DumAemModelTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build model with all fields")
        void shouldBuildModelWithAllFields() {
            List<DumAemTargetAttr> targetAttrs = new ArrayList<>();
            targetAttrs.add(DumAemTargetAttr.builder().name("title").build());

            DumAemModel model = DumAemModel.builder()
                    .type("cq:Page")
                    .subType("article")
                    .className("com.example.MyClass")
                    .validToIndex("true")
                    .targetAttrs(targetAttrs)
                    .build();

            assertEquals("cq:Page", model.getType());
            assertEquals("article", model.getSubType());
            assertEquals("com.example.MyClass", model.getClassName());
            assertEquals("true", model.getValidToIndex());
            assertEquals(1, model.getTargetAttrs().size());
        }

        @Test
        @DisplayName("Should have empty target attrs by default")
        void shouldHaveEmptyTargetAttrsByDefault() {
            DumAemModel model = DumAemModel.builder().build();

            assertNotNull(model.getTargetAttrs());
            assertTrue(model.getTargetAttrs().isEmpty());
        }
    }

    @Nested
    @DisplayName("NoArgs Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create model with default values")
        void shouldCreateModelWithDefaultValues() {
            DumAemModel model = new DumAemModel();

            assertNull(model.getType());
            assertNull(model.getSubType());
            assertNull(model.getClassName());
            assertNull(model.getValidToIndex());
        }
    }

    @Nested
    @DisplayName("AllArgs Constructor Tests")
    class AllArgsConstructorTests {

        @Test
        @DisplayName("Should create model with all args")
        void shouldCreateModelWithAllArgs() {
            List<DumAemTargetAttr> targetAttrs = Arrays.asList(
                    DumAemTargetAttr.builder().name("attr1").build(),
                    DumAemTargetAttr.builder().name("attr2").build());

            DumAemModel model = new DumAemModel("type", "subType", "className", "valid", targetAttrs);

            assertEquals("type", model.getType());
            assertEquals("subType", model.getSubType());
            assertEquals("className", model.getClassName());
            assertEquals("valid", model.getValidToIndex());
            assertEquals(2, model.getTargetAttrs().size());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get type")
        void shouldSetAndGetType() {
            DumAemModel model = new DumAemModel();
            model.setType("dam:Asset");

            assertEquals("dam:Asset", model.getType());
        }

        @Test
        @DisplayName("Should set and get subType")
        void shouldSetAndGetSubType() {
            DumAemModel model = new DumAemModel();
            model.setSubType("image");

            assertEquals("image", model.getSubType());
        }

        @Test
        @DisplayName("Should set and get className")
        void shouldSetAndGetClassName() {
            DumAemModel model = new DumAemModel();
            model.setClassName("com.example.Handler");

            assertEquals("com.example.Handler", model.getClassName());
        }

        @Test
        @DisplayName("Should set and get validToIndex")
        void shouldSetAndGetValidToIndex() {
            DumAemModel model = new DumAemModel();
            model.setValidToIndex("expression");

            assertEquals("expression", model.getValidToIndex());
        }

        @Test
        @DisplayName("Should set and get targetAttrs")
        void shouldSetAndGetTargetAttrs() {
            DumAemModel model = new DumAemModel();
            List<DumAemTargetAttr> targetAttrs = new ArrayList<>();
            targetAttrs.add(DumAemTargetAttr.builder().name("test").build());
            model.setTargetAttrs(targetAttrs);

            assertEquals(1, model.getTargetAttrs().size());
        }
    }

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should have toString implementation")
        void shouldHaveToStringImplementation() {
            DumAemModel model = DumAemModel.builder()
                    .type("cq:Page")
                    .build();

            String result = model.toString();

            assertNotNull(result);
            assertTrue(result.contains("cq:Page"));
        }
    }
}
