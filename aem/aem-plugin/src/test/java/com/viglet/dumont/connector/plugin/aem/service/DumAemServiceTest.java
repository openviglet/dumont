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

package com.viglet.dumont.connector.plugin.aem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.Locale;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;
import com.viglet.dumont.connector.aem.commons.bean.DumAemTargetAttrValueMap;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemModel;
import com.viglet.dumont.connector.plugin.aem.DumAemAttrProcess;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;

@ExtendWith(MockitoExtension.class)
@DisplayName("DumAemService Tests")
class DumAemServiceTest {

    @Mock
    private DumAemAttrProcess dumAemAttrProcess;

    private DumAemService dumAemService;

    @BeforeEach
    void setUp() {
        dumAemService = new DumAemService(dumAemAttrProcess);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create service with dependencies")
        void shouldCreateServiceWithDependencies() {
            assertNotNull(dumAemService);
        }
    }

    @Nested
    @DisplayName("getProviderName Tests")
    class GetProviderNameTests {

        @Test
        @DisplayName("Should return AEM as provider name")
        void shouldReturnAemAsProviderName() {
            assertEquals("AEM", dumAemService.getProviderName());
        }
    }

    @Nested
    @DisplayName("getTargetAttrValueMap Tests")
    class GetTargetAttrValueMapTests {

        @Test
        @DisplayName("Should return target attribute value map")
        void shouldReturnTargetAttrValueMap() {
            DumAemTargetAttrValueMap expectedMap = new DumAemTargetAttrValueMap();
            when(dumAemAttrProcess.prepareAttributeDefs(any(), any())).thenReturn(expectedMap);

            DumAemModel model = DumAemModel.builder()
                    .type("cq:Page")
                    .build();
            DumAemSession session = createMockSession(model);
            DumAemObject aemObject = createMockAemObject();

            DumAemTargetAttrValueMap result = dumAemService.getTargetAttrValueMap(session, aemObject);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("isNotValidType Tests")
    class IsNotValidTypeTests {

        @Test
        @DisplayName("Should return false for cq:Page type")
        void shouldReturnFalseForCqPageType() {
            DumAemModel model = DumAemModel.builder()
                    .type("cq:Page")
                    .build();
            DumAemObjectGeneric aemObject = createMockAemObjectGeneric();

            boolean result = dumAemService.isNotValidType(model, aemObject, "cq:Page");

            assertFalse(result);
        }

        @Test
        @DisplayName("Should return true for unknown type")
        void shouldReturnTrueForUnknownType() {
            DumAemModel model = DumAemModel.builder()
                    .type("cq:Page")
                    .build();
            DumAemObjectGeneric aemObject = createMockAemObjectGeneric();

            boolean result = dumAemService.isNotValidType(model, aemObject, "unknown:Type");

            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("isPage Tests")
    class IsPageTests {

        @Test
        @DisplayName("Should return true for cq:Page type")
        void shouldReturnTrueForCqPageType() {
            assertTrue(dumAemService.isPage("cq:Page"));
        }

        @Test
        @DisplayName("Should return false for non-page type")
        void shouldReturnFalseForNonPageType() {
            assertFalse(dumAemService.isPage("dam:Asset"));
        }
    }

    @Nested
    @DisplayName("isStaticFile Tests")
    class IsStaticFileTests {

        @Test
        @DisplayName("Should return true for static file asset")
        void shouldReturnTrueForStaticFileAsset() {
            DumAemModel model = DumAemModel.builder()
                    .type("dam:Asset")
                    .subType("static-file")
                    .build();

            boolean result = dumAemService.isStaticFile(model, "dam:Asset");

            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false for non-static file asset")
        void shouldReturnFalseForNonStaticFileAsset() {
            DumAemModel model = DumAemModel.builder()
                    .type("dam:Asset")
                    .subType("content-fragment")
                    .build();

            boolean result = dumAemService.isStaticFile(model, "dam:Asset");

            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false for page type")
        void shouldReturnFalseForPageType() {
            DumAemModel model = DumAemModel.builder()
                    .type("cq:Page")
                    .subType("static-file")
                    .build();

            boolean result = dumAemService.isStaticFile(model, "cq:Page");

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("isContentFragment Tests")
    class IsContentFragmentTests {

        @Test
        @DisplayName("Should return true for content fragment")
        void shouldReturnTrueForContentFragment() {
            DumAemModel model = DumAemModel.builder()
                    .type("dam:Asset")
                    .subType("contentFragment")
                    .build();
            DumAemObjectGeneric aemObject = createContentFragmentObject();

            boolean result = dumAemService.isContentFragment(model, "dam:Asset", aemObject);

            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false when not a content fragment type")
        void shouldReturnFalseWhenNotContentFragmentType() {
            DumAemModel model = DumAemModel.builder()
                    .type("dam:Asset")
                    .subType("static-file")
                    .build();
            DumAemObjectGeneric aemObject = createContentFragmentObject();

            boolean result = dumAemService.isContentFragment(model, "dam:Asset", aemObject);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("isAsset Tests")
    class IsAssetTests {

        @Test
        @DisplayName("Should return true for dam:Asset with subType")
        void shouldReturnTrueForDamAssetWithSubType() {
            DumAemModel model = DumAemModel.builder()
                    .type("dam:Asset")
                    .subType("content-fragment")
                    .build();

            boolean result = dumAemService.isAsset(model, "dam:Asset");

            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false for dam:Asset without subType")
        void shouldReturnFalseForDamAssetWithoutSubType() {
            DumAemModel model = DumAemModel.builder()
                    .type("dam:Asset")
                    .build();

            boolean result = dumAemService.isAsset(model, "dam:Asset");

            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false for non-asset type")
        void shouldReturnFalseForNonAssetType() {
            DumAemModel model = DumAemModel.builder()
                    .type("cq:Page")
                    .subType("content-fragment")
                    .build();

            boolean result = dumAemService.isAsset(model, "cq:Page");

            assertFalse(result);
        }
    }

    private DumAemSession createMockSession(DumAemModel model) {
        DumAemConfiguration configuration = mock(DumAemConfiguration.class, withSettings().lenient());
        lenient().when(configuration.getUrl()).thenReturn("http://localhost:4502");
        lenient().when(configuration.getUsername()).thenReturn("admin");
        lenient().when(configuration.getPassword()).thenReturn("admin");
        lenient().when(configuration.getDefaultLocale()).thenReturn(Locale.ENGLISH);
        lenient().when(configuration.isAuthor()).thenReturn(true);
        lenient().when(configuration.isPublish()).thenReturn(false);

        return DumAemSession.builder()
                .configuration(configuration)
                .model(model)
                .event(DumAemEvent.NONE)
                .source("test-source")
                .locale(Locale.ENGLISH)
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
                "/content/test/page", json, DumAemEvent.NONE);

        return new DumAemObject(aemObjectGeneric, DumAemEnv.AUTHOR);
    }

    private DumAemObjectGeneric createMockAemObjectGeneric() {
        JSONObject jcrContent = new JSONObject();
        jcrContent.put("jcr:title", "Test Page");

        JSONObject json = new JSONObject();
        json.put("jcr:content", jcrContent);
        json.put("jcr:primaryType", "cq:Page");

        return new DumAemObjectGeneric("/content/test/page", json, DumAemEvent.NONE);
    }

    private DumAemObjectGeneric createContentFragmentObject() {
        JSONObject jcrContent = new JSONObject();
        jcrContent.put("contentFragment", true);
        jcrContent.put("jcr:primaryType", "dam:AssetContent");

        JSONObject json = new JSONObject();
        json.put("jcr:content", jcrContent);
        json.put("jcr:primaryType", "dam:Asset");

        return new DumAemObjectGeneric("/content/dam/test/fragment", json, DumAemEvent.NONE);
    }
}
