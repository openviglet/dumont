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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DumAemTargetAttrDefinition Tests")
class DumAemTargetAttrDefinitionTest {

    @Nested
    @DisplayName("SuperBuilder Tests")
    class SuperBuilderTests {

        @Test
        @DisplayName("Should build target attr definition with inherited fields")
        void shouldBuildTargetAttrDefinitionWithInheritedFields() {
            DumAemTargetAttrDefinition definition = DumAemTargetAttrDefinition.builder()
                    .name("title")
                    .description("Title field")
                    .facet(true)
                    .mandatory(true)
                    .multiValued(true)
                    .build();

            assertEquals("title", definition.getName());
            assertEquals("Title field", definition.getDescription());
            assertTrue(definition.isFacet());
            assertTrue(definition.isMandatory());
            assertTrue(definition.isMultiValued());
        }

        @Test
        @DisplayName("Should build target attr definition with minimal fields")
        void shouldBuildTargetAttrDefinitionWithMinimalFields() {
            DumAemTargetAttrDefinition definition = DumAemTargetAttrDefinition.builder()
                    .name("content")
                    .build();

            assertEquals("content", definition.getName());
            assertNull(definition.getDescription());
        }
    }

    @Nested
    @DisplayName("AllArgsConstructor Tests")
    class AllArgsConstructorTests {

        @Test
        @DisplayName("Should extend TurSNAttributeSpec")
        void shouldExtendTurSNAttributeSpec() {
            DumAemTargetAttrDefinition definition = DumAemTargetAttrDefinition.builder()
                    .name("test")
                    .build();

            assertNotNull(definition);
            assertInstanceOf(com.viglet.turing.client.sn.job.TurSNAttributeSpec.class, definition);
        }
    }
}
