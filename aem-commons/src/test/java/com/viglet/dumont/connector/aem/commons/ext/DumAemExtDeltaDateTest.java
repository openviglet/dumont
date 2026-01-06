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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
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

@DisplayName("DumAemExtDeltaDate Tests")
class DumAemExtDeltaDateTest {

    private DumAemExtDeltaDate extDeltaDate;
    private DumAemConfiguration configuration;
    private SimpleDateFormat aemJsonDateFormat;

    @BeforeEach
    void setUp() {
        extDeltaDate = new DumAemExtDeltaDate();
        configuration = DumAemConfiguration.builder().build();
        aemJsonDateFormat = new SimpleDateFormat(DumAemConstants.DATE_JSON_FORMAT, Locale.ENGLISH);
    }

    @Nested
    @DisplayName("consume Method Tests")
    class ConsumeTests {

        @Test
        @DisplayName("Should return delta date from last modified")
        void shouldReturnDeltaDateFromLastModified() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            Calendar lastModified = Calendar.getInstance();
            lastModified.set(2024, Calendar.MARCH, 20, 14, 0, 0);
            String lastModifiedStr = aemJsonDateFormat.format(lastModified.getTime());

            JSONObject jcrContent = new JSONObject();
            jcrContent.put("cq:lastModified", lastModifiedStr);
            jcrNode.put("jcr:content", jcrContent);

            DumAemObject aemObject = new DumAemObject("/content/mysite/en/home", jcrNode, DumAemEnv.AUTHOR);

            Date result = extDeltaDate.consume(aemObject, configuration);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should return delta date when only created date exists")
        void shouldReturnDeltaDateWhenOnlyCreatedDateExists() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", "cq:Page");

            Calendar created = Calendar.getInstance();
            created.set(2024, Calendar.JANUARY, 15, 10, 30, 0);
            String createdStr = aemJsonDateFormat.format(created.getTime());
            jcrNode.put("jcr:created", createdStr);

            JSONObject jcrContent = new JSONObject();
            jcrNode.put("jcr:content", jcrContent);

            DumAemObject aemObject = new DumAemObject("/content/mysite/en/home", jcrNode, DumAemEnv.AUTHOR);

            Date result = extDeltaDate.consume(aemObject, configuration);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Interface Implementation Tests")
    class InterfaceTests {

        @Test
        @DisplayName("Should implement DumAemExtDeltaDateInterface")
        void shouldImplementDumAemExtDeltaDateInterface() {
            assertTrue(extDeltaDate instanceof DumAemExtDeltaDateInterface);
        }
    }
}
