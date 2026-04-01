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

import static org.junit.jupiter.api.Assertions.assertFalse;
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

@DisplayName("DumAemExtContentUrl Tests")
class DumAemExtContentUrlTest {

    private DumAemExtContentUrl extContentUrl;
    private DumAemConfiguration configuration;
    private DumAemTargetAttr targetAttr;
    private DumAemSourceAttr sourceAttr;

    @BeforeEach
    void setUp() {
        extContentUrl = new DumAemExtContentUrl();
        targetAttr = DumAemTargetAttr.builder().name("url").build();
        sourceAttr = DumAemSourceAttr.builder().build();
    }

    @Nested
    @DisplayName("getURL Static Method Tests")
    class GetURLTests {

        @Test
        @DisplayName("Should return URL with html extension for author env")
        void shouldReturnUrlWithHtmlExtensionForAuthor() {
            configuration = DumAemConfiguration.builder()
                    .url("http://localhost:4502")
                    .authorURLPrefix("http://author.example.com")
                    .build();

            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            DumAemObject aemObject = new DumAemObject("/content/mysite/en/home", jcrNode, DumAemEnv.AUTHOR);

            String result = DumAemExtContentUrl.getURL(aemObject, configuration);

            assertTrue(result.endsWith(".html"));
            assertTrue(result.contains("/content/mysite/en/home"));
        }

        @Test
        @DisplayName("Should return URL with html extension for publishing env")
        void shouldReturnUrlWithHtmlExtensionForPublishing() {
            configuration = DumAemConfiguration.builder()
                    .url("http://localhost:4503")
                    .publishURLPrefix("http://publish.example.com")
                    .build();

            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            DumAemObject aemObject = new DumAemObject("/content/mysite/en/about", jcrNode, DumAemEnv.PUBLISHING);

            String result = DumAemExtContentUrl.getURL(aemObject, configuration);

            assertTrue(result.endsWith(".html"));
            assertTrue(result.contains("/content/mysite/en/about"));
        }
    }

    @Nested
    @DisplayName("consume Method Tests")
    class ConsumeTests {

        @Test
        @DisplayName("Should consume and return TurMultiValue with URL")
        void shouldConsumeAndReturnTurMultiValueWithUrl() {
            configuration = DumAemConfiguration.builder()
                    .url("http://localhost:4502")
                    .authorURLPrefix("http://author.example.com")
                    .build();

            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            DumAemObject aemObject = new DumAemObject("/content/test/page", jcrNode, DumAemEnv.AUTHOR);

            TurMultiValue result = extContentUrl.consume(targetAttr, sourceAttr, aemObject, configuration);

            assertNotNull(result);
            assertFalse(result.isEmpty());
            assertTrue(result.get(0).endsWith(".html"));
        }
    }

    @Nested
    @DisplayName("Interface Implementation Tests")
    class InterfaceTests {

        @Test
        @DisplayName("Should implement DumAemExtAttributeInterface")
        void shouldImplementDumAemExtAttributeInterface() {
            assertTrue(extContentUrl instanceof DumAemExtAttributeInterface);
        }
    }
}
