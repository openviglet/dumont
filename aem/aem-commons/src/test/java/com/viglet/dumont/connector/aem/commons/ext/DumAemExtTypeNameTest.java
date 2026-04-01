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

package com.viglet.dumont.connector.aem.commons.ext;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemSourceAttr;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemTargetAttr;
import com.viglet.turing.client.sn.TurMultiValue;

@DisplayName("DumAemExtTypeName Tests")
class DumAemExtTypeNameTest {

    private DumAemExtTypeName extTypeName;
    private DumAemConfiguration configuration;
    private DumAemTargetAttr targetAttr;
    private DumAemSourceAttr sourceAttr;

    @BeforeEach
    void setUp() {
        extTypeName = new DumAemExtTypeName();
        configuration = DumAemConfiguration.builder().build();
        targetAttr = DumAemTargetAttr.builder().name("type").build();
        sourceAttr = DumAemSourceAttr.builder().build();
    }

    @Nested
    @DisplayName("consume Method Tests")
    class ConsumeTests {

        @Test
        @DisplayName("Should return cq:Page type")
        void shouldReturnCqPageType() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            DumAemObject aemObject = new DumAemObject("/content/mysite/en/home", jcrNode, DumAemEnv.AUTHOR);

            TurMultiValue result = extTypeName.consume(targetAttr, sourceAttr, aemObject, configuration);

            assertNotNull(result);
            assertEquals("cq:Page", result.get(0));
        }

        @Test
        @DisplayName("Should return dam:Asset type")
        void shouldReturnDamAssetType() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "dam:Asset");

            DumAemObject aemObject = new DumAemObject("/content/dam/images/logo.png", jcrNode, DumAemEnv.PUBLISHING);

            TurMultiValue result = extTypeName.consume(targetAttr, sourceAttr, aemObject, configuration);

            assertNotNull(result);
            assertEquals("dam:Asset", result.get(0));
        }

        @Test
        @DisplayName("Should return different primary types")
        void shouldReturnDifferentPrimaryTypes() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "nt:unstructured");

            DumAemObject aemObject = new DumAemObject("/content/test", jcrNode, DumAemEnv.AUTHOR);

            TurMultiValue result = extTypeName.consume(targetAttr, sourceAttr, aemObject, configuration);

            assertNotNull(result);
            assertEquals("nt:unstructured", result.get(0));
        }
    }

    @Nested
    @DisplayName("Interface Implementation Tests")
    class InterfaceTests {

        @Test
        @DisplayName("Should implement DumAemExtAttributeInterface")
        void shouldImplementDumAemExtAttributeInterface() {
            assertTrue(extTypeName instanceof DumAemExtAttributeInterface);
        }
    }
}
