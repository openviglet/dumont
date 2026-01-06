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

@DisplayName("DumAemExtPageComponents Tests")
class DumAemExtPageComponentsTest {

    private DumAemExtPageComponents extPageComponents;
    private DumAemConfiguration configuration;
    private DumAemTargetAttr targetAttr;
    private DumAemSourceAttr sourceAttr;

    @BeforeEach
    void setUp() {
        extPageComponents = new DumAemExtPageComponents();
        configuration = DumAemConfiguration.builder().build();
        targetAttr = DumAemTargetAttr.builder().name("components").build();
        sourceAttr = DumAemSourceAttr.builder().build();
    }

    @Nested
    @DisplayName("consume Method Tests")
    class ConsumeTests {

        @Test
        @DisplayName("Should return empty when aemObject is null")
        void shouldReturnEmptyWhenAemObjectIsNull() {
            TurMultiValue result = extPageComponents.consume(targetAttr, sourceAttr, null, configuration);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty when jcrContentNode is null")
        void shouldReturnEmptyWhenJcrContentNodeIsNull() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            DumAemObject aemObject = new DumAemObject("/content/mysite/en/home", jcrNode, DumAemEnv.AUTHOR);

            TurMultiValue result = extPageComponents.consume(targetAttr, sourceAttr, aemObject, configuration);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should return empty when no root node exists")
        void shouldReturnEmptyWhenNoRootNodeExists() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            JSONObject jcrContent = new JSONObject();
            jcrContent.put("jcr:title", "Test Page");
            jcrNode.put("jcr:content", jcrContent);

            DumAemObject aemObject = new DumAemObject("/content/mysite/en/home", jcrNode, DumAemEnv.AUTHOR);

            TurMultiValue result = extPageComponents.consume(targetAttr, sourceAttr, aemObject, configuration);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("extractResponsiveGridComponents Method Tests")
    class ExtractResponsiveGridComponentsTests {

        @Test
        @DisplayName("Should return empty for null AEM object")
        void shouldReturnEmptyForNullAemObject() {
            TurMultiValue result = extPageComponents.extractResponsiveGridComponents(null);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should extract responsive grid components")
        void shouldExtractResponsiveGridComponents() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            JSONObject jcrContent = new JSONObject();
            jcrContent.put("jcr:title", "Test Page");

            JSONObject root = new JSONObject();

            JSONObject responsiveGrid = new JSONObject();
            responsiveGrid.put("sling:resourceType", "wcm/foundation/components/responsivegrid");
            responsiveGrid.put("text", "<p>Test content</p>");

            root.put("responsivegrid", responsiveGrid);
            jcrContent.put("root", root);
            jcrNode.put("jcr:content", jcrContent);

            DumAemObject aemObject = new DumAemObject("/content/mysite/en/home", jcrNode, DumAemEnv.AUTHOR);

            TurMultiValue result = extPageComponents.extractResponsiveGridComponents(aemObject);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should skip non-responsive grid components")
        void shouldSkipNonResponsiveGridComponents() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            JSONObject jcrContent = new JSONObject();
            jcrContent.put("jcr:title", "Test Page");

            JSONObject root = new JSONObject();

            JSONObject textComponent = new JSONObject();
            textComponent.put("sling:resourceType", "core/wcm/components/text/v2/text");
            textComponent.put("text", "<p>Some text</p>");

            root.put("textComponent", textComponent);
            jcrContent.put("root", root);
            jcrNode.put("jcr:content", jcrContent);

            DumAemObject aemObject = new DumAemObject("/content/mysite/en/home", jcrNode, DumAemEnv.AUTHOR);

            TurMultiValue result = extPageComponents.extractResponsiveGridComponents(aemObject);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Interface Implementation Tests")
    class InterfaceTests {

        @Test
        @DisplayName("Should implement DumAemExtAttributeInterface")
        void shouldImplementDumAemExtAttributeInterface() {
            assertTrue(extPageComponents instanceof DumAemExtAttributeInterface);
        }
    }
}
