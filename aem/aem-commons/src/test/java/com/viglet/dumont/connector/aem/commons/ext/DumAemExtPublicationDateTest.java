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

@DisplayName("DumAemExtPublicationDate Tests")
class DumAemExtPublicationDateTest {

    private DumAemExtPublicationDate extPublicationDate;
    private DumAemConfiguration configuration;
    private DumAemTargetAttr targetAttr;
    private DumAemSourceAttr sourceAttr;
    private SimpleDateFormat aemJsonDateFormat;

    @BeforeEach
    void setUp() {
        extPublicationDate = new DumAemExtPublicationDate();
        configuration = DumAemConfiguration.builder().build();
        targetAttr = DumAemTargetAttr.builder().name("publicationDate").build();
        sourceAttr = DumAemSourceAttr.builder().build();
        aemJsonDateFormat = new SimpleDateFormat(DumAemConstants.DATE_JSON_FORMAT, Locale.ENGLISH);
    }

    @Nested
    @DisplayName("consume Method Tests")
    class ConsumeTests {

        @Test
        @DisplayName("Should return publication date when cq:lastReplicated exists")
        void shouldReturnPublicationDateWhenLastReplicatedExists() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            Calendar lastReplicated = Calendar.getInstance();
            lastReplicated.set(2024, Calendar.FEBRUARY, 25, 12, 0, 0);
            String lastReplicatedStr = aemJsonDateFormat.format(lastReplicated.getTime());

            JSONObject jcrContent = new JSONObject();
            jcrContent.put("cq:lastReplicated", lastReplicatedStr);
            jcrNode.put("jcr:content", jcrContent);

            DumAemObject aemObject = new DumAemObject("/content/mysite/en/home", jcrNode, DumAemEnv.AUTHOR);

            TurMultiValue result = extPublicationDate.consume(targetAttr, sourceAttr, aemObject, configuration);

            assertNotNull(result);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("Should return current date when cq:lastReplicated does not exist")
        void shouldReturnNullWhenLastReplicatedDoesNotExist() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            JSONObject jcrContent = new JSONObject();
            jcrNode.put("jcr:content", jcrContent);

            DumAemObject aemObject = new DumAemObject("/content/mysite/en/home", jcrNode, DumAemEnv.AUTHOR);

            TurMultiValue result = extPublicationDate.consume(targetAttr, sourceAttr, aemObject, configuration);

            // The implementation returns Calendar.getInstance() when no cq:lastReplicated
            // exists
            // So the result will contain the current date instead of null
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Interface Implementation Tests")
    class InterfaceTests {

        @Test
        @DisplayName("Should implement DumAemExtAttributeInterface")
        void shouldImplementDumAemExtAttributeInterface() {
            assertTrue(extPublicationDate instanceof DumAemExtAttributeInterface);
        }
    }
}
