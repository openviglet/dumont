/*
 * Copyright (C) 2016-2024 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.dumont.connector.plugin.aem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;

@DisplayName("DumAemObjectService Tests")
class DumAemObjectServiceTest {

    private DumAemObjectService service;

    @BeforeEach
    void setUp() {
        service = new DumAemObjectService();
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {
        @Test
        @DisplayName("Should create instance")
        void shouldCreateInstance() {
            assertNotNull(service);
        }
    }

    @Nested
    @DisplayName("GetDumAemObjectGeneric Without Event Tests")
    class GetDumAemObjectGenericWithoutEventTests {
        @Test
        @DisplayName("Should create object with path and JSON")
        void shouldCreateObjectWithPathAndJson() {
            JSONObject json = new JSONObject();
            json.put("jcr:primaryType", "cq:Page");

            DumAemObjectGeneric result = service.getDumAemObjectGeneric("/content/test", json, DumAemEvent.NONE);

            assertNotNull(result);
            assertEquals("/content/test", result.getPath());
        }

        @Test
        @DisplayName("Should handle page type JSON")
        void shouldHandlePageTypeJson() {
            JSONObject json = new JSONObject();
            json.put("jcr:primaryType", "cq:Page");
            JSONObject jcrContent = new JSONObject();
            jcrContent.put("jcr:title", "Test Page");
            json.put("jcr:content", jcrContent);

            DumAemObjectGeneric result = service.getDumAemObjectGeneric("/content/test/page", json, DumAemEvent.NONE);

            assertNotNull(result);
            assertEquals("/content/test/page", result.getPath());
        }

        @Test
        @DisplayName("Should handle asset type JSON")
        void shouldHandleAssetTypeJson() {
            JSONObject json = new JSONObject();
            json.put("jcr:primaryType", "dam:Asset");

            DumAemObjectGeneric result = service.getDumAemObjectGeneric("/content/dam/test.jpg", json,
                    DumAemEvent.NONE);

            assertNotNull(result);
            assertEquals("/content/dam/test.jpg", result.getPath());
        }

        @Test
        @DisplayName("Should handle empty JSON object")
        void shouldHandleEmptyJsonObject() {
            JSONObject json = new JSONObject();

            DumAemObjectGeneric result = service.getDumAemObjectGeneric("/content/test", json, DumAemEvent.NONE);

            assertNotNull(result);
            assertEquals("/content/test", result.getPath());
        }
    }

    @Nested
    @DisplayName("GetDumAemObjectGeneric With Event Tests")
    class GetDumAemObjectGenericWithEventTests {
        @Test
        @DisplayName("Should create object with PUBLISHING event")
        void shouldCreateObjectWithPublishingEvent() {
            JSONObject json = new JSONObject();
            json.put("jcr:primaryType", "cq:Page");

            DumAemObjectGeneric result = service.getDumAemObjectGeneric(
                    "/content/test", json, DumAemEvent.PUBLISHING);

            assertNotNull(result);
            assertEquals("/content/test", result.getPath());
        }

        @Test
        @DisplayName("Should create object with UNPUBLISHING event")
        void shouldCreateObjectWithUnpublishingEvent() {
            JSONObject json = new JSONObject();
            json.put("jcr:primaryType", "cq:Page");

            DumAemObjectGeneric result = service.getDumAemObjectGeneric(
                    "/content/test", json, DumAemEvent.UNPUBLISHING);

            assertNotNull(result);
            assertEquals("/content/test", result.getPath());
        }

        @Test
        @DisplayName("Should create object with NONE event")
        void shouldCreateObjectWithNoneEvent() {
            JSONObject json = new JSONObject();
            json.put("jcr:primaryType", "cq:Page");

            DumAemObjectGeneric result = service.getDumAemObjectGeneric(
                    "/content/test", json, DumAemEvent.NONE);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Various Content Types Tests")
    class VariousContentTypesTests {
        @Test
        @DisplayName("Should handle content fragment")
        void shouldHandleContentFragment() {
            JSONObject json = new JSONObject();
            json.put("jcr:primaryType", "dam:Asset");
            JSONObject jcrContent = new JSONObject();
            JSONObject data = new JSONObject();
            data.put("cq:model", "/conf/test/model");
            jcrContent.put("data", data);
            json.put("jcr:content", jcrContent);

            DumAemObjectGeneric result = service.getDumAemObjectGeneric(
                    "/content/dam/test/cf", json, DumAemEvent.NONE);

            assertNotNull(result);
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource({
                "sling:Folder, /content/test/folder",
                "sling:OrderedFolder, /content/test/ordered-folder",
                "nt:unstructured, /content/test/data"
        })
        @DisplayName("Should handle folder and unstructured types")
        void shouldHandleFolderAndUnstructuredTypes(String primaryType, String path) {
            JSONObject json = new JSONObject();
            json.put("jcr:primaryType", primaryType);

            DumAemObjectGeneric result = service.getDumAemObjectGeneric(path, json, DumAemEvent.NONE);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Complex JSON Structure Tests")
    class ComplexJsonStructureTests {
        @Test
        @DisplayName("Should handle nested JSON structures")
        void shouldHandleNestedJsonStructures() {
            JSONObject json = new JSONObject();
            json.put("jcr:primaryType", "cq:Page");

            JSONObject jcrContent = new JSONObject();
            jcrContent.put("jcr:title", "Test");
            jcrContent.put("sling:resourceType", "mysite/components/page");

            JSONObject par = new JSONObject();
            par.put("jcr:primaryType", "nt:unstructured");
            jcrContent.put("par", par);

            json.put("jcr:content", jcrContent);

            DumAemObjectGeneric result = service.getDumAemObjectGeneric(
                    "/content/test/page", json, DumAemEvent.NONE);

            assertNotNull(result);
            assertNotNull(result.getJcrNode());
        }
    }
}
