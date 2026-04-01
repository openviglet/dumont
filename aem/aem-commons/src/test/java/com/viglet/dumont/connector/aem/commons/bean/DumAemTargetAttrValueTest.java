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

package com.viglet.dumont.connector.aem.commons.bean;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.turing.client.sn.TurMultiValue;

@DisplayName("DumAemTargetAttrValue Tests")
class DumAemTargetAttrValueTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create with target attr name and multi value")
        void shouldCreateWithTargetAttrNameAndMultiValue() {
            TurMultiValue multiValue = TurMultiValue.singleItem("test value");
            DumAemTargetAttrValue attrValue = new DumAemTargetAttrValue("title", multiValue);

            assertEquals("title", attrValue.getTargetAttrName());
            assertEquals(multiValue, attrValue.getMultiValue());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        private DumAemTargetAttrValue attrValue;

        @BeforeEach
        void setUp() {
            TurMultiValue multiValue = TurMultiValue.singleItem("initial");
            attrValue = new DumAemTargetAttrValue("attr", multiValue);
        }

        @Test
        @DisplayName("Should get target attr name")
        void shouldGetTargetAttrName() {
            assertEquals("attr", attrValue.getTargetAttrName());
        }

        @Test
        @DisplayName("Should set target attr name")
        void shouldSetTargetAttrName() {
            attrValue.setTargetAttrName("newAttr");

            assertEquals("newAttr", attrValue.getTargetAttrName());
        }

        @Test
        @DisplayName("Should get multi value")
        void shouldGetMultiValue() {
            assertNotNull(attrValue.getMultiValue());
            assertEquals("initial", attrValue.getMultiValue().get(0));
        }

        @Test
        @DisplayName("Should set multi value")
        void shouldSetMultiValue() {
            TurMultiValue newMultiValue = TurMultiValue.singleItem("new value");
            attrValue.setMultiValue(newMultiValue);

            assertEquals("new value", attrValue.getMultiValue().get(0));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle null target attr name")
        void shouldHandleNullTargetAttrName() {
            TurMultiValue multiValue = TurMultiValue.singleItem("test");
            DumAemTargetAttrValue attrValue = new DumAemTargetAttrValue(null, multiValue);

            assertNull(attrValue.getTargetAttrName());
        }

        @Test
        @DisplayName("Should handle null multi value")
        void shouldHandleNullMultiValue() {
            DumAemTargetAttrValue attrValue = new DumAemTargetAttrValue("attr", null);

            assertNull(attrValue.getMultiValue());
        }

        @Test
        @DisplayName("Should handle empty target attr name")
        void shouldHandleEmptyTargetAttrName() {
            TurMultiValue multiValue = TurMultiValue.singleItem("test");
            DumAemTargetAttrValue attrValue = new DumAemTargetAttrValue("", multiValue);

            assertEquals("", attrValue.getTargetAttrName());
        }
    }
}
