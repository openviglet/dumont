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

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemSourceAttr;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemTargetAttr;

@DisplayName("DumAemContext Tests")
class DumAemContextTest {

    private DumAemObject aemObject;
    private DumAemContext context;

    @BeforeEach
    void setUp() {
        JSONObject jcrNode = new JSONObject();
        jcrNode.put("jcr:primaryType", "cq:Page");

        aemObject = new DumAemObject("/content/test", jcrNode, DumAemEnv.AUTHOR);
        context = new DumAemContext(aemObject);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create context with AEM object")
        void shouldCreateContextWithAemObject() {
            assertNotNull(context.getCmsObjectInstance());
            assertEquals(aemObject, context.getCmsObjectInstance());
        }

        @Test
        @DisplayName("Should initialize target attr as null")
        void shouldInitializeTargetAttrAsNull() {
            assertNull(context.getDumAemTargetAttr());
        }

        @Test
        @DisplayName("Should initialize source attr as null")
        void shouldInitializeSourceAttrAsNull() {
            assertNull(context.getDumAemSourceAttr());
        }
    }

    @Nested
    @DisplayName("Setter Tests")
    class SetterTests {

        @Test
        @DisplayName("Should set target attribute")
        void shouldSetTargetAttribute() {
            DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                    .name("title")
                    .build();

            context.setDumAemTargetAttr(targetAttr);

            assertEquals(targetAttr, context.getDumAemTargetAttr());
        }

        @Test
        @DisplayName("Should set source attribute")
        void shouldSetSourceAttribute() {
            DumAemSourceAttr sourceAttr = DumAemSourceAttr.builder()
                    .name("jcr:title")
                    .build();

            context.setDumAemSourceAttr(sourceAttr);

            assertEquals(sourceAttr, context.getDumAemSourceAttr());
        }

        @Test
        @DisplayName("Should set CMS object instance")
        void shouldSetCmsObjectInstance() {
            JSONObject newJcrNode = new JSONObject();
            newJcrNode.put("jcr:primaryType", "dam:Asset");
            DumAemObject newAemObject = new DumAemObject("/content/dam/test", newJcrNode, DumAemEnv.PUBLISHING);

            context.setCmsObjectInstance(newAemObject);

            assertEquals(newAemObject, context.getCmsObjectInstance());
        }
    }

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should have toString implementation")
        void shouldHaveToStringImplementation() {
            String result = context.toString();

            assertNotNull(result);
            assertTrue(result.contains("DumAemContext"));
        }
    }
}
