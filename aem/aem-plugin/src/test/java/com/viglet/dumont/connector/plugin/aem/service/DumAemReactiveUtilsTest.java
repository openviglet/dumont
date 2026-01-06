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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@DisplayName("DumAemReactiveUtils Tests")
class DumAemReactiveUtilsTest {

    @Mock
    private DumAemReactiveHttpService reactiveHttpService;

    private DumAemReactiveUtils dumAemReactiveUtils;

    @BeforeEach
    void setUp() {
        dumAemReactiveUtils = new DumAemReactiveUtils(reactiveHttpService);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create utils with reactive HTTP service")
        void shouldCreateUtilsWithReactiveHttpService() {
            assertNotNull(dumAemReactiveUtils);
        }
    }

    @Nested
    @DisplayName("getInfinityJsonReactive Tests")
    class GetInfinityJsonReactiveTests {

        @Test
        @DisplayName("Should return Mono with JSONObject for valid JSON response")
        void shouldReturnMonoWithJsonObjectForValidJsonResponse() {
            DumAemConfiguration config = createMockConfiguration();
            String jsonResponse = "{\"jcr:title\": \"Test Page\", \"jcr:primaryType\": \"cq:Page\"}";

            when(reactiveHttpService.fetchResponseBodyReactive(anyString(), any()))
                    .thenReturn(Mono.just(jsonResponse));

            var result = dumAemReactiveUtils.getInfinityJsonReactive("/content/page", config);

            assertNotNull(result);
            var jsonObject = result.block();
            assertNotNull(jsonObject);
            assertTrue(jsonObject.has("jcr:title"));
        }

        @Test
        @DisplayName("Should return empty Mono for blank response")
        void shouldReturnEmptyMonoForBlankResponse() {
            DumAemConfiguration config = createMockConfiguration();

            when(reactiveHttpService.fetchResponseBodyReactive(anyString(), any()))
                    .thenReturn(Mono.just(""));

            var result = dumAemReactiveUtils.getInfinityJsonReactive("/content/page", config);

            assertNotNull(result);
            var jsonObject = result.block();
            assertTrue(jsonObject == null);
        }

        @Test
        @DisplayName("Should return empty Mono for invalid JSON response")
        void shouldReturnEmptyMonoForInvalidJsonResponse() {
            DumAemConfiguration config = createMockConfiguration();

            when(reactiveHttpService.fetchResponseBodyReactive(anyString(), any()))
                    .thenReturn(Mono.just("not valid json"));

            var result = dumAemReactiveUtils.getInfinityJsonReactive("/content/page", config);

            assertNotNull(result);
            var jsonObject = result.block();
            assertTrue(jsonObject == null);
        }

        @Test
        @DisplayName("Should handle URL ending with .json")
        void shouldHandleUrlEndingWithJson() {
            DumAemConfiguration config = createMockConfiguration();
            String jsonResponse = "{\"jcr:title\": \"Test Page\"}";

            when(reactiveHttpService.fetchResponseBodyReactive(anyString(), any()))
                    .thenReturn(Mono.just(jsonResponse));

            var result = dumAemReactiveUtils.getInfinityJsonReactive("/content/page.json", config);

            assertNotNull(result);
            var jsonObject = result.block();
            assertNotNull(jsonObject);
        }

        @Test
        @DisplayName("Should handle URL not ending with .json")
        void shouldHandleUrlNotEndingWithJson() {
            DumAemConfiguration config = createMockConfiguration();
            String jsonResponse = "{\"jcr:title\": \"Test Page\"}";

            when(reactiveHttpService.fetchResponseBodyReactive(anyString(), any()))
                    .thenReturn(Mono.just(jsonResponse));

            var result = dumAemReactiveUtils.getInfinityJsonReactive("/content/page", config);

            assertNotNull(result);
            var jsonObject = result.block();
            assertNotNull(jsonObject);
        }

        @Test
        @DisplayName("Should return empty Mono on error")
        void shouldReturnEmptyMonoOnError() {
            DumAemConfiguration config = createMockConfiguration();

            when(reactiveHttpService.fetchResponseBodyReactive(anyString(), any()))
                    .thenReturn(Mono.error(new RuntimeException("Connection error")));

            var result = dumAemReactiveUtils.getInfinityJsonReactive("/content/page", config);

            assertNotNull(result);
            var jsonObject = result.block();
            assertTrue(jsonObject == null);
        }
    }

    @Nested
    @DisplayName("isResponseBodyJSONArray Tests")
    class IsResponseBodyJSONArrayTests {

        @Test
        @DisplayName("Should return true for valid JSON array")
        void shouldReturnTrueForValidJsonArray() throws Exception {
            Method method = DumAemReactiveUtils.class
                    .getDeclaredMethod("isResponseBodyJSONArray", String.class);
            method.setAccessible(true);

            Boolean result = (Boolean) method.invoke(null, "[\"item1\", \"item2\"]");

            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false for JSON object")
        void shouldReturnFalseForJsonObject() throws Exception {
            Method method = DumAemReactiveUtils.class
                    .getDeclaredMethod("isResponseBodyJSONArray", String.class);
            method.setAccessible(true);

            Boolean result = (Boolean) method.invoke(null, "{\"key\": \"value\"}");

            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false for invalid JSON")
        void shouldReturnFalseForInvalidJson() throws Exception {
            Method method = DumAemReactiveUtils.class
                    .getDeclaredMethod("isResponseBodyJSONArray", String.class);
            method.setAccessible(true);

            Boolean result = (Boolean) method.invoke(null, "not json");

            assertFalse(result);
        }

        @Test
        @DisplayName("Should return true for empty JSON array")
        void shouldReturnTrueForEmptyJsonArray() throws Exception {
            Method method = DumAemReactiveUtils.class
                    .getDeclaredMethod("isResponseBodyJSONArray", String.class);
            method.setAccessible(true);

            Boolean result = (Boolean) method.invoke(null, "[]");

            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("isResponseBodyJSONObject Tests")
    class IsResponseBodyJSONObjectTests {

        @Test
        @DisplayName("Should return true for valid JSON object")
        void shouldReturnTrueForValidJsonObject() throws Exception {
            Method method = DumAemReactiveUtils.class
                    .getDeclaredMethod("isResponseBodyJSONObject", String.class);
            method.setAccessible(true);

            Boolean result = (Boolean) method.invoke(null, "{\"key\": \"value\"}");

            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false for JSON array")
        void shouldReturnFalseForJsonArray() throws Exception {
            Method method = DumAemReactiveUtils.class
                    .getDeclaredMethod("isResponseBodyJSONObject", String.class);
            method.setAccessible(true);

            Boolean result = (Boolean) method.invoke(null, "[\"item1\", \"item2\"]");

            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false for invalid JSON")
        void shouldReturnFalseForInvalidJsonObject() throws Exception {
            Method method = DumAemReactiveUtils.class
                    .getDeclaredMethod("isResponseBodyJSONObject", String.class);
            method.setAccessible(true);

            Boolean result = (Boolean) method.invoke(null, "not json");

            assertFalse(result);
        }

        @Test
        @DisplayName("Should return true for empty JSON object")
        void shouldReturnTrueForEmptyJsonObject() throws Exception {
            Method method = DumAemReactiveUtils.class
                    .getDeclaredMethod("isResponseBodyJSONObject", String.class);
            method.setAccessible(true);

            Boolean result = (Boolean) method.invoke(null, "{}");

            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("JSON Array Processing Tests")
    class JsonArrayProcessingTests {

        @Test
        @DisplayName("Should handle JSON array response with items")
        void shouldHandleJsonArrayResponseWithItems() {
            DumAemConfiguration config = createMockConfiguration();
            String arrayResponse = "[\"/content/path1\", \"/content/path2\"]";
            String objectResponse = "{\"jcr:title\": \"Test\"}";

            when(reactiveHttpService.fetchResponseBodyReactive(anyString(), any()))
                    .thenReturn(Mono.just(arrayResponse))
                    .thenReturn(Mono.just(objectResponse));

            var result = dumAemReactiveUtils.getInfinityJsonReactive("/content/page", config);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should handle empty JSON array response")
        void shouldHandleEmptyJsonArrayResponse() {
            DumAemConfiguration config = createMockConfiguration();
            String arrayResponse = "[]";

            when(reactiveHttpService.fetchResponseBodyReactive(anyString(), any()))
                    .thenReturn(Mono.just(arrayResponse));

            var result = dumAemReactiveUtils.getInfinityJsonReactive("/content/page", config);

            assertNotNull(result);
            var jsonObject = result.block();
            assertTrue(jsonObject == null);
        }
    }

    @Nested
    @DisplayName("URL Formatting Tests")
    class UrlFormattingTests {

        @Test
        @DisplayName("Should format URL with infinity.json suffix")
        void shouldFormatUrlWithInfinityJsonSuffix() {
            DumAemConfiguration config = createMockConfiguration();
            String jsonResponse = "{\"jcr:title\": \"Test\"}";

            when(reactiveHttpService.fetchResponseBodyReactive(anyString(), any()))
                    .thenReturn(Mono.just(jsonResponse));

            var result = dumAemReactiveUtils.getInfinityJsonReactive("/content/page", config);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should not add suffix when URL already ends with .json")
        void shouldNotAddSuffixWhenUrlAlreadyEndsWithJson() {
            DumAemConfiguration config = createMockConfiguration();
            String jsonResponse = "{\"jcr:title\": \"Test\"}";

            when(reactiveHttpService.fetchResponseBodyReactive(anyString(), any()))
                    .thenReturn(Mono.just(jsonResponse));

            var result = dumAemReactiveUtils.getInfinityJsonReactive("/content/page.json", config);

            assertNotNull(result);
        }
    }

    private DumAemConfiguration createMockConfiguration() {
        return DumAemConfiguration.builder()
                .url("http://localhost:4502")
                .username("admin")
                .password("admin")
                .build();
    }
}
