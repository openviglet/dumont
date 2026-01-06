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

@DisplayName("DumAemSourceAttr Tests")
class DumAemSourceAttrTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build source attr with all fields")
        void shouldBuildSourceAttrWithAllFields() {
            DumAemSourceAttr sourceAttr = DumAemSourceAttr.builder()
                    .name("jcr:title")
                    .className("com.example.Handler")
                    .uniqueValues(true)
                    .convertHtmlToText(true)
                    .build();

            assertEquals("jcr:title", sourceAttr.getName());
            assertEquals("com.example.Handler", sourceAttr.getClassName());
            assertTrue(sourceAttr.isUniqueValues());
            assertTrue(sourceAttr.isConvertHtmlToText());
        }

        @Test
        @DisplayName("Should build source attr with defaults")
        void shouldBuildSourceAttrWithDefaults() {
            DumAemSourceAttr sourceAttr = DumAemSourceAttr.builder()
                    .name("title")
                    .build();

            assertEquals("title", sourceAttr.getName());
            assertNull(sourceAttr.getClassName());
            assertFalse(sourceAttr.isUniqueValues());
            assertFalse(sourceAttr.isConvertHtmlToText());
        }
    }

    @Nested
    @DisplayName("NoArgs Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create source attr with default values")
        void shouldCreateSourceAttrWithDefaultValues() {
            DumAemSourceAttr sourceAttr = new DumAemSourceAttr();

            assertNull(sourceAttr.getName());
            assertNull(sourceAttr.getClassName());
            assertFalse(sourceAttr.isUniqueValues());
            assertFalse(sourceAttr.isConvertHtmlToText());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get name")
        void shouldSetAndGetName() {
            DumAemSourceAttr sourceAttr = new DumAemSourceAttr();
            sourceAttr.setName("description");

            assertEquals("description", sourceAttr.getName());
        }

        @Test
        @DisplayName("Should set and get className")
        void shouldSetAndGetClassName() {
            DumAemSourceAttr sourceAttr = new DumAemSourceAttr();
            sourceAttr.setClassName("com.example.Processor");

            assertEquals("com.example.Processor", sourceAttr.getClassName());
        }

        @Test
        @DisplayName("Should set and get uniqueValues")
        void shouldSetAndGetUniqueValues() {
            DumAemSourceAttr sourceAttr = new DumAemSourceAttr();
            sourceAttr.setUniqueValues(true);

            assertTrue(sourceAttr.isUniqueValues());
        }

        @Test
        @DisplayName("Should set and get convertHtmlToText")
        void shouldSetAndGetConvertHtmlToText() {
            DumAemSourceAttr sourceAttr = new DumAemSourceAttr();
            sourceAttr.setConvertHtmlToText(true);

            assertTrue(sourceAttr.isConvertHtmlToText());
        }
    }

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should have toString implementation")
        void shouldHaveToStringImplementation() {
            DumAemSourceAttr sourceAttr = DumAemSourceAttr.builder()
                    .name("title")
                    .build();

            String result = sourceAttr.toString();

            assertNotNull(result);
            assertTrue(result.contains("title"));
        }
    }
}
