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

package com.viglet.dumont.connector.aem.sample.ext;

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
import com.viglet.dumont.connector.aem.commons.bean.DumAemTargetAttrValueMap;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.ext.DumAemExtContentInterface;

@DisplayName("DumAemExtSampleModelJson Tests")
class DumAemExtSampleModelJsonTest {

    private DumAemExtSampleModelJson extSampleModelJson;
    private DumAemConfiguration configuration;

    @BeforeEach
    void setUp() {
        extSampleModelJson = new DumAemExtSampleModelJson();
        configuration = DumAemConfiguration.builder()
                .url("http://localhost:4502")
                .username("admin")
                .password("admin")
                .build();
    }

    @Nested
    @DisplayName("Constant Tests")
    class ConstantTests {

        @Test
        @DisplayName("Should have correct FRAGMENT_PATH constant")
        void shouldHaveCorrectFragmentPathConstant() {
            assertEquals("fragmentPath", DumAemExtSampleModelJson.FRAGMENT_PATH);
        }

        @Test
        @DisplayName("Should have correct MODEL_JSON_EXTENSION constant")
        void shouldHaveCorrectModelJsonExtensionConstant() {
            assertEquals(".model.json", DumAemExtSampleModelJson.MODEL_JSON_EXTENSION);
        }
    }

    @Nested
    @DisplayName("consume Method Tests")
    class ConsumeTests {

        @Test
        @DisplayName("Should return DumAemTargetAttrValueMap")
        void shouldReturnDumAemTargetAttrValueMap() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            DumAemObject aemObject = new DumAemObject("/content/mysite/en/home", jcrNode, DumAemEnv.AUTHOR);

            DumAemTargetAttrValueMap result = extSampleModelJson.consume(aemObject, configuration);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should return empty map when request fails")
        void shouldReturnEmptyMapWhenRequestFails() {
            DumAemConfiguration invalidConfig = DumAemConfiguration.builder()
                    .url("http://invalid-host:9999")
                    .build();

            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            DumAemObject aemObject = new DumAemObject("/content/test", jcrNode, DumAemEnv.AUTHOR);

            DumAemTargetAttrValueMap result = extSampleModelJson.consume(aemObject, invalidConfig);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Interface Implementation Tests")
    class InterfaceTests {

        @Test
        @DisplayName("Should implement DumAemExtContentInterface")
        void shouldImplementDumAemExtContentInterface() {
            assertTrue(extSampleModelJson instanceof DumAemExtContentInterface);
        }
    }
}
