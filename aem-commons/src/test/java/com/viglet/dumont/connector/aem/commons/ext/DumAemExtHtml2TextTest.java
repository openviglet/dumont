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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

@DisplayName("DumAemExtHtml2Text Tests")
class DumAemExtHtml2TextTest {

    private DumAemExtHtml2Text extHtml2Text;
    private DumAemConfiguration configuration;
    private DumAemTargetAttr targetAttr;
    private DumAemSourceAttr sourceAttr;

    @BeforeEach
    void setUp() {
        extHtml2Text = new DumAemExtHtml2Text();
        configuration = DumAemConfiguration.builder().build();
        targetAttr = DumAemTargetAttr.builder().name("text").build();
    }

    @Nested
    @DisplayName("consume Method Tests")
    class ConsumeTests {

        @Test
        @DisplayName("Should convert HTML to plain text")
        void shouldConvertHtmlToPlainText() {
            sourceAttr = DumAemSourceAttr.builder()
                    .name("htmlContent")
                    .build();

            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");
            JSONObject jcrContent = new JSONObject();
            jcrContent.put("htmlContent", "<p>Hello <strong>World</strong></p>");
            jcrNode.put("jcr:content", jcrContent);

            DumAemObject aemObject = new DumAemObject("/content/mysite/en/home", jcrNode, DumAemEnv.AUTHOR);

            TurMultiValue result = extHtml2Text.consume(targetAttr, sourceAttr, aemObject, configuration);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should return empty string when source attr name is null")
        void shouldReturnEmptyStringWhenSourceAttrNameIsNull() {
            sourceAttr = DumAemSourceAttr.builder().build();

            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            DumAemObject aemObject = new DumAemObject("/content/mysite/en/home", jcrNode, DumAemEnv.AUTHOR);

            TurMultiValue result = extHtml2Text.consume(targetAttr, sourceAttr, aemObject, configuration);

            assertNotNull(result);
            assertEquals("", result.get(0));
        }

        @Test
        @DisplayName("Should return empty string when attribute not found")
        void shouldReturnEmptyStringWhenAttributeNotFound() {
            sourceAttr = DumAemSourceAttr.builder()
                    .name("nonExistentAttribute")
                    .build();

            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            DumAemObject aemObject = new DumAemObject("/content/mysite/en/home", jcrNode, DumAemEnv.AUTHOR);

            TurMultiValue result = extHtml2Text.consume(targetAttr, sourceAttr, aemObject, configuration);

            assertNotNull(result);
            assertEquals("", result.get(0));
        }

        @Test
        @DisplayName("Should return empty string when aemObject is null")
        void shouldReturnEmptyStringWhenAemObjectIsNull() {
            sourceAttr = DumAemSourceAttr.builder()
                    .name("content")
                    .build();

            TurMultiValue result = extHtml2Text.consume(targetAttr, sourceAttr, null, configuration);

            assertNotNull(result);
            assertEquals("", result.get(0));
        }

        @Test
        @DisplayName("Should handle complex HTML")
        void shouldHandleComplexHtml() {
            sourceAttr = DumAemSourceAttr.builder()
                    .name("richText")
                    .build();

            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");
            JSONObject jcrContent = new JSONObject();
            jcrContent.put("richText",
                    "<div><h1>Title</h1><p>Paragraph <a href='link'>with link</a></p><ul><li>Item 1</li><li>Item 2</li></ul></div>");
            jcrNode.put("jcr:content", jcrContent);

            DumAemObject aemObject = new DumAemObject("/content/mysite/en/home", jcrNode, DumAemEnv.AUTHOR);

            TurMultiValue result = extHtml2Text.consume(targetAttr, sourceAttr, aemObject, configuration);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Interface Implementation Tests")
    class InterfaceTests {

        @Test
        @DisplayName("Should implement DumAemExtAttributeInterface")
        void shouldImplementDumAemExtAttributeInterface() {
            assertTrue(extHtml2Text instanceof DumAemExtAttributeInterface);
        }
    }
}
