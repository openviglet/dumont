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

package com.viglet.dumont.connector.plugin.aem;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.utils.DumAemPluginUtils;

@DisplayName("DumAemPluginUtils Tests")
class DumAemPluginUtilsTest {

        @Nested
        @DisplayName("getObjectDetailForLogs with DumAemSession Tests")
        class GetObjectDetailForLogsWithSessionTests {

                @Test
                @DisplayName("Should return formatted object detail string")
                void shouldReturnFormattedObjectDetailString() {
                        DumAemConfiguration configuration = DumAemConfiguration.builder()
                                        .id("config-123")
                                        .defaultLocale(Locale.US)
                                        .build();

                        DumAemSession dumAemSession = DumAemSession.builder()
                                        .configuration(configuration)
                                        .transactionId("tx-456")
                                        .build();

                        JSONObject jcrNode = new JSONObject();
                        jcrNode.put("jcr:primaryType", "cq:Page");

                        DumAemObjectGeneric aemObject = new DumAemObjectGeneric("/content/mysite/en/home", jcrNode,
                                        DumAemEvent.PUBLISHING);

                        String result = DumAemPluginUtils.getObjectDetailForLogs(dumAemSession, aemObject);

                        assertNotNull(result);
                        assertTrue(result.contains("/content/mysite/en/home"));
                        assertTrue(result.contains("config-123"));
                        assertTrue(result.contains("tx-456"));
                }

                @Test
                @DisplayName("Should include PUBLISHING environment in log")
                void shouldIncludePublishingEnvironmentInLog() {
                        DumAemConfiguration configuration = DumAemConfiguration.builder()
                                        .id("test-config")
                                        .build();

                        DumAemSession dumAemSession = DumAemSession.builder()
                                        .configuration(configuration)
                                        .transactionId("transaction-id")
                                        .build();

                        JSONObject jcrNode = new JSONObject();
                        jcrNode.put("jcr:primaryType", "cq:Page");

                        DumAemObjectGeneric aemObject = new DumAemObjectGeneric("/content/test", jcrNode,
                                        DumAemEvent.PUBLISHING);

                        String result = DumAemPluginUtils.getObjectDetailForLogs(dumAemSession, aemObject);

                        assertTrue(result.contains("PUBLISHING"));
                }
        }

        @Nested
        @DisplayName("getObjectDetailForLogs with DumConnectorSession Tests")
        class GetObjectDetailForLogsWithConnectorSessionTests {

                @Test
                @DisplayName("Should return formatted object detail string for connector session")
                void shouldReturnFormattedObjectDetailStringForConnectorSession() {
                        DumConnectorIndexing indexing = DumConnectorIndexing.builder()
                                        .environment("author")
                                        .locale(Locale.forLanguageTag("pt-BR"))
                                        .build();

                        DumConnectorSession session = DumConnectorSession.builder()
                                        .source("aem-source")
                                        .transactionId("tx-789")
                                        .build();

                        String result = DumAemPluginUtils.getObjectDetailForLogs("/content/path", indexing, session);

                        assertNotNull(result);
                        assertTrue(result.contains("/content/path"));
                        assertTrue(result.contains("aem-source"));
                        assertTrue(result.contains("tx-789"));
                        assertTrue(result.contains("author"));
                }

                @Test
                @DisplayName("Should handle different content IDs")
                void shouldHandleDifferentContentIds() {
                        DumConnectorIndexing indexing = DumConnectorIndexing.builder()
                                        .environment("publish")
                                        .locale(Locale.US)
                                        .build();

                        DumConnectorSession session = DumConnectorSession.builder()
                                        .source("test-source")
                                        .transactionId("test-tx")
                                        .build();

                        String result1 = DumAemPluginUtils.getObjectDetailForLogs("/content/page1", indexing, session);
                        String result2 = DumAemPluginUtils.getObjectDetailForLogs("/content/dam/asset", indexing,
                                        session);

                        assertTrue(result1.contains("/content/page1"));
                        assertTrue(result2.contains("/content/dam/asset"));
                }
        }
}
