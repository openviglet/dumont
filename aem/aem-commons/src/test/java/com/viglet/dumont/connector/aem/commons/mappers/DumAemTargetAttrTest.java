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

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DumAemTargetAttr Tests")
class DumAemTargetAttrTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build target attr with all fields")
        void shouldBuildTargetAttrWithAllFields() {
            List<DumAemSourceAttr> sourceAttrs = Arrays.asList(
                    DumAemSourceAttr.builder().name("jcr:title").build(),
                    DumAemSourceAttr.builder().name("jcr:description").build());

            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                    .name("title")
                    .textValue("Static Text")
                    .sourceAttrs(sourceAttrs)
                    .build();

            assertEquals("title", targetAttr.getName());
            assertEquals("Static Text", targetAttr.getTextValue());
            assertEquals(2, targetAttr.getSourceAttrs().size());
        }

        @Test
        @DisplayName("Should build target attr with name only")
        void shouldBuildTargetAttrWithNameOnly() {
            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                    .name("description")
                    .build();

            assertEquals("description", targetAttr.getName());
            assertNull(targetAttr.getTextValue());
            assertNull(targetAttr.getSourceAttrs());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get name")
        void shouldSetAndGetName() {
            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder().build();
            targetAttr.setName("newName");

            assertEquals("newName", targetAttr.getName());
        }

        @Test
        @DisplayName("Should set and get textValue")
        void shouldSetAndGetTextValue() {
            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder().build();
            targetAttr.setTextValue("New Text Value");

            assertEquals("New Text Value", targetAttr.getTextValue());
        }

        @Test
        @DisplayName("Should set and get sourceAttrs")
        void shouldSetAndGetSourceAttrs() {
            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder().build();
            List<DumAemSourceAttr> sourceAttrs = Arrays.asList(
                    DumAemSourceAttr.builder().name("attr1").build());
            targetAttr.setSourceAttrs(sourceAttrs);

            assertEquals(1, targetAttr.getSourceAttrs().size());
        }
    }

    @Nested
    @DisplayName("Inheritance Tests")
    class InheritanceTests {

        @Test
        @DisplayName("Should inherit from DumAemTargetAttrDefinition")
        void shouldInheritFromDumAemTargetAttrDefinition() {
            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                    .name("title")
                    .description("Title attribute")
                    .facet(true)
                    .mandatory(true)
                    .build();

            assertEquals("title", targetAttr.getName());
            assertEquals("Title attribute", targetAttr.getDescription());
            assertTrue(targetAttr.isFacet());
            assertTrue(targetAttr.isMandatory());
        }
    }

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should have toString implementation")
        void shouldHaveToStringImplementation() {
            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                    .name("title")
                    .textValue("Test")
                    .build();

            String result = targetAttr.toString();

            assertNotNull(result);
        }
    }
}
