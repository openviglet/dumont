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
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemSourceAttr;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemTargetAttr;
import com.viglet.turing.client.sn.TurMultiValue;

@DisplayName("DumAemExtContentId Tests")
class DumAemExtContentIdTest {

    private DumAemExtContentId extContentId;
    private DumAemConfiguration configuration;
    private DumAemTargetAttr targetAttr;
    private DumAemSourceAttr sourceAttr;

    @BeforeEach
    void setUp() {
        extContentId = new DumAemExtContentId();
        configuration = DumAemConfiguration.builder().build();
        targetAttr = DumAemTargetAttr.builder().name("id").build();
        sourceAttr = DumAemSourceAttr.builder().build();
    }

    @Test
    @DisplayName("Should return path as content id")
    void shouldReturnPathAsContentId() {
        JSONObject jcrNode = new JSONObject();
        jcrNode.put("jcr:primaryType", "cq:Page");

        DumAemObject aemObject = new DumAemObject("/content/mysite/en/home", jcrNode, DumAemEnv.AUTHOR);

        TurMultiValue result = extContentId.consume(targetAttr, sourceAttr, aemObject, configuration);

        assertNotNull(result);
        assertEquals("/content/mysite/en/home", result.get(0));
    }

    @Test
    @DisplayName("Should handle different paths")
    void shouldHandleDifferentPaths() {
        JSONObject jcrNode = new JSONObject();
        jcrNode.put("jcr:primaryType", "dam:Asset");

        DumAemObject aemObject = new DumAemObject("/content/dam/images/logo.png", jcrNode, DumAemEnv.PUBLISHING);

        TurMultiValue result = extContentId.consume(targetAttr, sourceAttr, aemObject, configuration);

        assertEquals("/content/dam/images/logo.png", result.get(0));
    }
}
