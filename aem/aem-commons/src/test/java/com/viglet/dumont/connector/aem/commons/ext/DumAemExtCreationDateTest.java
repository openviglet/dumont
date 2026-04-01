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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.DumAemConstants;
import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemSourceAttr;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemTargetAttr;
import com.viglet.turing.client.sn.TurMultiValue;

@DisplayName("DumAemExtCreationDate Tests")
class DumAemExtCreationDateTest {

    private DumAemExtCreationDate extCreationDate;
    private DumAemConfiguration configuration;
    private DumAemTargetAttr targetAttr;
    private DumAemSourceAttr sourceAttr;
    private SimpleDateFormat aemJsonDateFormat;

    @BeforeEach
    void setUp() {
        extCreationDate = new DumAemExtCreationDate();
        configuration = DumAemConfiguration.builder().build();
        targetAttr = DumAemTargetAttr.builder().name("creationDate").build();
        sourceAttr = DumAemSourceAttr.builder().build();
        aemJsonDateFormat = new SimpleDateFormat(DumAemConstants.DATE_JSON_FORMAT, Locale.ENGLISH);
    }

    @Nested
    @DisplayName("consume Method Tests")
    class ConsumeTests {

        @Test
        @DisplayName("Should return creation date when jcr:created exists")
        void shouldReturnCreationDateWhenJcrCreatedExists() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            Calendar created = Calendar.getInstance();
            created.set(2024, Calendar.JANUARY, 15, 10, 30, 0);
            String dateStr = aemJsonDateFormat.format(created.getTime());
            jcrNode.put("jcr:created", dateStr);

            DumAemObject aemObject = new DumAemObject("/content/mysite/en/home", jcrNode, DumAemEnv.AUTHOR);

            TurMultiValue result = extCreationDate.consume(targetAttr, sourceAttr, aemObject, configuration);

            assertNotNull(result);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("Should return null when jcr:created does not exist")
        void shouldReturnNullWhenJcrCreatedDoesNotExist() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            DumAemObject aemObject = new DumAemObject("/content/mysite/en/home", jcrNode, DumAemEnv.AUTHOR);

            TurMultiValue result = extCreationDate.consume(targetAttr, sourceAttr, aemObject, configuration);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Interface Implementation Tests")
    class InterfaceTests {

        @Test
        @DisplayName("Should implement DumAemExtAttributeInterface")
        void shouldImplementDumAemExtAttributeInterface() {
            assertTrue(extCreationDate instanceof DumAemExtAttributeInterface);
        }
    }
}
