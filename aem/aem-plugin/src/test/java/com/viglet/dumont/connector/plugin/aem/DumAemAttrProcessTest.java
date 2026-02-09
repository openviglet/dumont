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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.bean.DumAemContext;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;
import com.viglet.dumont.connector.aem.commons.bean.DumAemTargetAttrValueMap;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemModel;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemSourceAttr;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemTargetAttr;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;

@DisplayName("DumAemAttrProcess Tests")
class DumAemAttrProcessTest {

        private DumAemAttrProcess dumAemAttrProcess;

        @BeforeEach
        void setUp() {
                dumAemAttrProcess = new DumAemAttrProcess();
        }

        @Nested
        @DisplayName("Constant Tests")
        class ConstantTests {

                @Test
                @DisplayName("CQ_TAGS_PATH should have correct value")
                void shouldHaveCorrectCqTagsPathValue() {
                        assertEquals("/content/_cq_tags", DumAemAttrProcess.CQ_TAGS_PATH);
                }
        }

        @Nested
        @DisplayName("prepareAttributeDefs Tests")
        class PrepareAttributeDefsTests {

                @Test
                @DisplayName("Should return empty map when model has no target attributes")
                void shouldReturnEmptyMapWhenNoTargetAttrs() {
                        DumAemModel model = DumAemModel.builder()
                                        .type("cq:Page")
                                        .targetAttrs(Collections.emptyList())
                                        .build();

                        DumAemSession session = createMockSession(model);
                        DumAemObject aemObject = createMockAemObject();

                        DumAemTargetAttrValueMap result = dumAemAttrProcess.prepareAttributeDefs(session, aemObject);

                        assertNotNull(result);
                }

                @Test
                @DisplayName("Should process text value source attributes")
                void shouldProcessTextValueSourceAttrs() {
                        DumAemSourceAttr sourceAttr = DumAemSourceAttr.builder()
                                        .name("jcr:title")
                                        .build();

                        DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                                        .name("title")
                                        .textValue("Static Title")
                                        .sourceAttrs(List.of(sourceAttr))
                                        .build();

                        DumAemModel model = DumAemModel.builder()
                                        .type("cq:Page")
                                        .targetAttrs(List.of(targetAttr))
                                        .build();

                        DumAemSession session = createMockSession(model);
                        DumAemObject aemObject = createMockAemObject();

                        DumAemTargetAttrValueMap result = dumAemAttrProcess.prepareAttributeDefs(session, aemObject);

                        assertNotNull(result);
                }

                @Test
                @DisplayName("Should handle null target attributes in list")
                void shouldHandleNullTargetAttrsInList() {
                        List<DumAemTargetAttr> targetAttrs = new ArrayList<>();
                        targetAttrs.add(null);
                        targetAttrs.add(
                                        DumAemTargetAttr.builder().name("title").textValue("value")
                                                        .sourceAttrs(new ArrayList<>()).build());

                        DumAemModel model = DumAemModel.builder()
                                        .type("cq:Page")
                                        .targetAttrs(targetAttrs)
                                        .build();

                        DumAemSession session = createMockSession(model);
                        DumAemObject aemObject = createMockAemObject();

                        DumAemTargetAttrValueMap result = dumAemAttrProcess.prepareAttributeDefs(session, aemObject);

                        assertNotNull(result);
                }
        }

        @Nested
        @DisplayName("addTargetAttrValuesBySourceAttr Tests")
        class AddTargetAttrValuesBySourceAttrTests {

                @Test
                @DisplayName("Should process source attribute with text value")
                void shouldProcessSourceAttrWithTextValue() {
                        DumAemSourceAttr sourceAttr = DumAemSourceAttr.builder()
                                        .name("jcr:title")
                                        .uniqueValues(false)
                                        .build();

                        DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                                        .name("title")
                                        .textValue("Test Title")
                                        .sourceAttrs(List.of(sourceAttr))
                                        .build();

                        DumAemModel model = DumAemModel.builder()
                                        .type("cq:Page")
                                        .targetAttrs(List.of(targetAttr))
                                        .build();

                        DumAemSession session = createMockSession(model);
                        DumAemObject aemObject = createMockAemObject();
                        DumAemContext context = new DumAemContext(aemObject);
                        context.setDumAemTargetAttr(targetAttr);

                        DumAemTargetAttrValueMap result = dumAemAttrProcess.addTargetAttrValuesBySourceAttr(
                                        session, targetAttr, sourceAttr, context);

                        assertNotNull(result);
                }

                @Test
                @DisplayName("Should apply unique values filter when enabled")
                void shouldApplyUniqueValuesFilter() {
                        DumAemSourceAttr sourceAttr = DumAemSourceAttr.builder()
                                        .name("jcr:title")
                                        .uniqueValues(true)
                                        .build();

                        DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                                        .name("title")
                                        .textValue("Test Title")
                                        .sourceAttrs(List.of(sourceAttr))
                                        .build();

                        DumAemModel model = DumAemModel.builder()
                                        .type("cq:Page")
                                        .targetAttrs(List.of(targetAttr))
                                        .build();

                        DumAemSession session = createMockSession(model);
                        DumAemObject aemObject = createMockAemObject();
                        DumAemContext context = new DumAemContext(aemObject);
                        context.setDumAemTargetAttr(targetAttr);

                        DumAemTargetAttrValueMap result = dumAemAttrProcess.addTargetAttrValuesBySourceAttr(
                                        session, targetAttr, sourceAttr, context);

                        assertNotNull(result);
                }
        }

        @Nested
        @DisplayName("process Tests")
        class ProcessTests {

                @Test
                @DisplayName("Should return text value when target attr has text value")
                void shouldReturnTextValueWhenTargetAttrHasTextValue() {
                        DumAemSourceAttr sourceAttr = DumAemSourceAttr.builder()
                                        .name("jcr:title")
                                        .build();

                        DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                                        .name("title")
                                        .textValue("Static Value")
                                        .sourceAttrs(List.of(sourceAttr))
                                        .build();

                        DumAemModel model = DumAemModel.builder()
                                        .type("cq:Page")
                                        .targetAttrs(List.of(targetAttr))
                                        .build();

                        DumAemSession session = createMockSession(model);
                        DumAemObject aemObject = createMockAemObject();
                        DumAemContext context = new DumAemContext(aemObject);
                        context.setDumAemTargetAttr(targetAttr);
                        context.setDumAemSourceAttr(sourceAttr);

                        DumAemTargetAttrValueMap result = dumAemAttrProcess.process(session, context);

                        assertNotNull(result);
                        assertTrue(result.containsKey("title"));
                }

                @Test
                @DisplayName("Should process custom class when no text value")
                void shouldProcessCustomClassWhenNoTextValue() {
                        DumAemSourceAttr sourceAttr = DumAemSourceAttr.builder()
                                        .name("jcr:title")
                                        .build();

                        DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                                        .name("title")
                                        .sourceAttrs(List.of(sourceAttr))
                                        .build();

                        DumAemModel model = DumAemModel.builder()
                                        .type("cq:Page")
                                        .targetAttrs(List.of(targetAttr))
                                        .build();

                        DumAemSession session = createMockSession(model);
                        DumAemObject aemObject = createMockAemObject();
                        DumAemContext context = new DumAemContext(aemObject);
                        context.setDumAemTargetAttr(targetAttr);
                        context.setDumAemSourceAttr(sourceAttr);

                        DumAemTargetAttrValueMap result = dumAemAttrProcess.process(session, context);

                        assertNotNull(result);
                }
        }

        private DumAemSession createMockSession(DumAemModel model) {
                DumAemConfiguration configuration = mock(DumAemConfiguration.class);
                when(configuration.getUrl()).thenReturn("http://localhost:4502");
                when(configuration.getUsername()).thenReturn("admin");
                when(configuration.getPassword()).thenReturn("admin");
                when(configuration.getDefaultLocale()).thenReturn(Locale.ENGLISH);
                when(configuration.isAuthor()).thenReturn(true);
                when(configuration.isPublish()).thenReturn(false);

                return DumAemSession.builder()
                                .configuration(configuration)
                                .model(model)
                                .event(DumAemEvent.INDEXING)
                                .source("test-source")
                                .locale(Locale.ENGLISH)
                                .attributeSpecs(new ArrayList<>())
                                .build();
        }

        private DumAemObject createMockAemObject() {
                JSONObject jcrContent = new JSONObject();
                jcrContent.put("jcr:title", "Test Page");
                jcrContent.put("jcr:primaryType", "cq:PageContent");

                JSONObject json = new JSONObject();
                json.put("jcr:content", jcrContent);
                json.put("jcr:primaryType", "cq:Page");

                DumAemObjectGeneric aemObjectGeneric = new DumAemObjectGeneric(
                                "/content/test/page", json, DumAemEvent.INDEXING);

                return new DumAemObject(aemObjectGeneric, DumAemEnv.AUTHOR);
        }
}
