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

package com.viglet.dumont.connector.aem.sample.beans;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DumAemSampleModel Tests")
class DumAemSampleModelTest {

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get fragmentPath")
        void shouldSetAndGetFragmentPath() {
            DumAemSampleModel model = new DumAemSampleModel();
            model.setFragmentPath("/content/dam/fragments/sample");

            assertEquals("/content/dam/fragments/sample", model.getFragmentPath());
        }

        @Test
        @DisplayName("Should have null fragmentPath by default")
        void shouldHaveNullFragmentPathByDefault() {
            DumAemSampleModel model = new DumAemSampleModel();

            assertNull(model.getFragmentPath());
        }

        @Test
        @DisplayName("Should handle different path values")
        void shouldHandleDifferentPathValues() {
            DumAemSampleModel model = new DumAemSampleModel();

            model.setFragmentPath("/content/dam/fragments/article1");
            assertEquals("/content/dam/fragments/article1", model.getFragmentPath());

            model.setFragmentPath("/content/dam/fragments/article2");
            assertEquals("/content/dam/fragments/article2", model.getFragmentPath());
        }

        @Test
        @DisplayName("Should handle empty string")
        void shouldHandleEmptyString() {
            DumAemSampleModel model = new DumAemSampleModel();
            model.setFragmentPath("");

            assertEquals("", model.getFragmentPath());
        }
    }
}
