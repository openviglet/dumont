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

package com.viglet.dumont.connector.aem.commons;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;

@DisplayName("DumAemObjectGeneric Tests")
class DumAemObjectGenericTest {

    private static final String TEST_PATH = "/content/mysite/en/home";
    private static final String TEST_TITLE = "Home Page";
    private static final String TEST_TEMPLATE = "/conf/mysite/settings/wcm/templates/page";
    private static final String TEST_TYPE = "cq:Page";

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create object with basic JCR node")
        void shouldCreateObjectWithBasicJcrNode() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", TEST_TYPE);

            DumAemObjectGeneric object = new DumAemObjectGeneric(TEST_PATH, jcrNode);

            assertEquals(TEST_PATH, object.getPath());
            assertEquals(TEST_TYPE, object.getType());
            assertEquals(TEST_PATH + ".html", object.getUrl());
            assertNotNull(object.getJcrNode());
        }

        @Test
        @DisplayName("Should handle missing primaryType")
        void shouldHandleMissingPrimaryType() {
            JSONObject jcrNode = new JSONObject();

            DumAemObjectGeneric object = new DumAemObjectGeneric(TEST_PATH, jcrNode);

            assertEquals("", object.getType());
        }

        @Test
        @DisplayName("Should create object with JCR content")
        void shouldCreateObjectWithJcrContent() {
            JSONObject jcrNode = createJcrNodeWithContent();

            DumAemObjectGeneric object = new DumAemObjectGeneric(TEST_PATH, jcrNode);

            assertEquals(TEST_TITLE, object.getTitle());
            assertEquals(TEST_TEMPLATE, object.getTemplate());
        }

        @Test
        @DisplayName("Should handle event PUBLISHING")
        void shouldHandleEventPublishing() {
            JSONObject jcrNode = createJcrNodeWithContent();

            DumAemObjectGeneric object = new DumAemObjectGeneric(TEST_PATH, jcrNode, DumAemEvent.PUBLISHING);

            assertTrue(object.isDelivered());
        }

        @Test
        @DisplayName("Should handle event UNPUBLISHING")
        void shouldHandleEventUnpublishing() {
            JSONObject jcrNode = createJcrNodeWithContent();

            DumAemObjectGeneric object = new DumAemObjectGeneric(TEST_PATH, jcrNode, DumAemEvent.UNPUBLISHING);

            assertFalse(object.isDelivered());
        }

        @Test
        @DisplayName("Should handle event NONE")
        void shouldHandleEventNone() {
            JSONObject jcrNode = createJcrNodeWithContent();

            DumAemObjectGeneric object = new DumAemObjectGeneric(TEST_PATH, jcrNode, DumAemEvent.NONE);

            assertNotNull(object);
        }
    }

    @Nested
    @DisplayName("Content Fragment Tests")
    class ContentFragmentTests {

        @Test
        @DisplayName("Should detect content fragment")
        void shouldDetectContentFragment() {
            JSONObject jcrContent = new JSONObject();
            jcrContent.put("contentFragment", true);

            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", TEST_TYPE);
            jcrNode.put("jcr:content", jcrContent);

            DumAemObjectGeneric object = new DumAemObjectGeneric(TEST_PATH, jcrNode);

            assertTrue(object.isContentFragment());
        }

        @Test
        @DisplayName("Should detect non content fragment")
        void shouldDetectNonContentFragment() {
            JSONObject jcrContent = new JSONObject();
            jcrContent.put("contentFragment", false);

            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", TEST_TYPE);
            jcrNode.put("jcr:content", jcrContent);

            DumAemObjectGeneric object = new DumAemObjectGeneric(TEST_PATH, jcrNode);

            assertFalse(object.isContentFragment());
        }
    }

    @Nested
    @DisplayName("Date Parsing Tests")
    class DateParsingTests {

        @Test
        @DisplayName("Should parse creation date")
        void shouldParseCreationDate() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", TEST_TYPE);
            jcrNode.put("jcr:created", "Mon Jan 01 2024 10:30:00 GMT+0000");

            DumAemObjectGeneric object = new DumAemObjectGeneric(TEST_PATH, jcrNode);

            assertNotNull(object.getCreatedDate());
        }

        @Test
        @DisplayName("Should check if string is date")
        void shouldCheckIfStringIsDate() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", TEST_TYPE);

            DumAemObjectGeneric object = new DumAemObjectGeneric(TEST_PATH, jcrNode);

            assertTrue(object.isDate("Mon Jan 01 2024 10:30:00 GMT+0000"));
            assertFalse(object.isDate("not a date"));
        }
    }

    @Nested
    @DisplayName("Dependencies Tests")
    class DependenciesTests {

        @Test
        @DisplayName("Should extract content dependencies")
        void shouldExtractContentDependencies() {
            JSONObject jcrContent = new JSONObject();
            jcrContent.put("reference", "/content/dam/mysite/image.jpg");

            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", TEST_TYPE);
            jcrNode.put("jcr:content", jcrContent);

            DumAemObjectGeneric object = new DumAemObjectGeneric(TEST_PATH, jcrNode);

            assertNotNull(object.getDependencies());
            assertTrue(object.getDependencies().contains("/content/dam/mysite/image.jpg"));
        }
    }

    @Nested
    @DisplayName("Data Path Tests")
    class DataPathTests {

        @Test
        @DisplayName("Should set data path and extract attributes")
        void shouldSetDataPathAndExtractAttributes() {
            JSONObject dataNode = new JSONObject();
            dataNode.put("title", "Test Title");
            dataNode.put("description", "Test Description");

            JSONObject jcrContent = new JSONObject();
            jcrContent.put("data", dataNode);

            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", TEST_TYPE);
            jcrNode.put("jcr:content", jcrContent);

            DumAemObjectGeneric object = new DumAemObjectGeneric(TEST_PATH, jcrNode);
            object.setDataPath("data");

            assertEquals("Test Title", object.getAttributes().get("title"));
            assertEquals("Test Description", object.getAttributes().get("description"));
        }

        @Test
        @DisplayName("Should handle null data path")
        void shouldHandleNullDataPath() {
            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", TEST_TYPE);

            DumAemObjectGeneric object = new DumAemObjectGeneric(TEST_PATH, jcrNode);
            object.setDataPath(null);

            assertTrue(object.getAttributes().isEmpty());
        }
    }

    @Nested
    @DisplayName("Model Tests")
    class ModelTests {

        @Test
        @DisplayName("Should extract model from data folder")
        void shouldExtractModelFromDataFolder() {
            JSONObject dataNode = new JSONObject();
            dataNode.put("cq:model", "/conf/mysite/settings/dam/cfm/models/article");

            JSONObject jcrContent = new JSONObject();
            jcrContent.put("data", dataNode);

            JSONObject jcrNode = new JSONObject();
            jcrNode.put("jcr:primaryType", TEST_TYPE);
            jcrNode.put("jcr:content", jcrContent);

            DumAemObjectGeneric object = new DumAemObjectGeneric(TEST_PATH, jcrNode);

            assertEquals("/conf/mysite/settings/dam/cfm/models/article", object.getModel());
        }
    }

    private JSONObject createJcrNodeWithContent() {
        JSONObject jcrContent = new JSONObject();
        jcrContent.put("jcr:title", TEST_TITLE);
        jcrContent.put("cq:template", TEST_TEMPLATE);

        JSONObject jcrNode = new JSONObject();
        jcrNode.put("jcr:primaryType", TEST_TYPE);
        jcrNode.put("jcr:content", jcrContent);

        return jcrNode;
    }
}
