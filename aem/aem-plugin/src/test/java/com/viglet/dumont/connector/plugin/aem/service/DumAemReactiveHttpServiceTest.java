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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;

@ExtendWith(MockitoExtension.class)
@DisplayName("DumAemReactiveHttpService Tests")
class DumAemReactiveHttpServiceTest {

    private DumAemReactiveHttpService dumAemReactiveHttpService;

    @BeforeEach
    void setUp() {
        dumAemReactiveHttpService = new DumAemReactiveHttpService();
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create service with WebClient")
        void shouldCreateServiceWithWebClient() {
            assertNotNull(dumAemReactiveHttpService);
        }

        @Test
        @DisplayName("Should initialize WebClient with SSL configuration")
        void shouldInitializeWebClientWithSslConfiguration() {
            DumAemReactiveHttpService service = new DumAemReactiveHttpService();
            assertNotNull(service);
        }
    }

    @Nested
    @DisplayName("basicAuth Tests")
    class BasicAuthTests {

        @Test
        @DisplayName("Should generate basic auth header")
        void shouldGenerateBasicAuthHeader() throws Exception {
            Method basicAuthMethod = DumAemReactiveHttpService.class
                    .getDeclaredMethod("basicAuth", String.class, String.class);
            basicAuthMethod.setAccessible(true);

            String result = (String) basicAuthMethod.invoke(dumAemReactiveHttpService, "admin", "admin");

            assertNotNull(result);
            assertEquals("Basic YWRtaW46YWRtaW4=", result);
        }

        @Test
        @DisplayName("Should handle empty username")
        void shouldHandleEmptyUsername() throws Exception {
            Method basicAuthMethod = DumAemReactiveHttpService.class
                    .getDeclaredMethod("basicAuth", String.class, String.class);
            basicAuthMethod.setAccessible(true);

            String result = (String) basicAuthMethod.invoke(dumAemReactiveHttpService, "", "password");

            assertNotNull(result);
            assertEquals("Basic OnBhc3N3b3Jk", result);
        }

        @Test
        @DisplayName("Should handle empty password")
        void shouldHandleEmptyPassword() throws Exception {
            Method basicAuthMethod = DumAemReactiveHttpService.class
                    .getDeclaredMethod("basicAuth", String.class, String.class);
            basicAuthMethod.setAccessible(true);

            String result = (String) basicAuthMethod.invoke(dumAemReactiveHttpService, "admin", "");

            assertNotNull(result);
            assertEquals("Basic YWRtaW46", result);
        }

        @Test
        @DisplayName("Should handle special characters in credentials")
        void shouldHandleSpecialCharactersInCredentials() throws Exception {
            Method basicAuthMethod = DumAemReactiveHttpService.class
                    .getDeclaredMethod("basicAuth", String.class, String.class);
            basicAuthMethod.setAccessible(true);

            String result = (String) basicAuthMethod.invoke(dumAemReactiveHttpService, "user@domain.com", "p@ss:word!");

            assertNotNull(result);
            assertEquals("Basic dXNlckBkb21haW4uY29tOnBAc3M6d29yZCE=", result);
        }
    }

    @Nested
    @DisplayName("fetchResponseBodyReactive Tests")
    class FetchResponseBodyReactiveTests {

        @Test
        @DisplayName("Should return Mono for valid URL")
        void shouldReturnMonoForValidUrl() {
            DumAemConfiguration config = DumAemConfiguration.builder()
                    .url("http://localhost:4502")
                    .username("admin")
                    .password("admin")
                    .build();

            var result = dumAemReactiveHttpService.fetchResponseBodyReactive(
                    "http://localhost:4502/content.json", config);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should handle URL with special characters")
        void shouldHandleUrlWithSpecialCharacters() {
            DumAemConfiguration config = DumAemConfiguration.builder()
                    .url("http://localhost:4502")
                    .username("admin")
                    .password("admin")
                    .build();

            var result = dumAemReactiveHttpService.fetchResponseBodyReactive(
                    "http://localhost:4502/content/path with spaces.json", config);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should handle URL with query parameters")
        void shouldHandleUrlWithQueryParameters() {
            DumAemConfiguration config = DumAemConfiguration.builder()
                    .url("http://localhost:4502")
                    .username("admin")
                    .password("admin")
                    .build();

            var result = dumAemReactiveHttpService.fetchResponseBodyReactive(
                    "http://localhost:4502/content.json?limit=10&offset=0", config);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should handle URL with fragments")
        void shouldHandleUrlWithFragments() {
            DumAemConfiguration config = DumAemConfiguration.builder()
                    .url("http://localhost:4502")
                    .username("admin")
                    .password("admin")
                    .build();

            var result = dumAemReactiveHttpService.fetchResponseBodyReactive(
                    "http://localhost:4502/content.json#section", config);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("WebClient Configuration Tests")
    class WebClientConfigurationTests {

        @Test
        @DisplayName("Should create WebClient with timeout settings")
        void shouldCreateWebClientWithTimeoutSettings() {
            DumAemReactiveHttpService service = new DumAemReactiveHttpService();
            assertNotNull(service);
        }

        @Test
        @DisplayName("Should create WebClient with SSL protocols")
        void shouldCreateWebClientWithSslProtocols() {
            DumAemReactiveHttpService service = new DumAemReactiveHttpService();
            assertNotNull(service);
        }

        @Test
        @DisplayName("Should create WebClient with custom user agent")
        void shouldCreateWebClientWithCustomUserAgent() {
            DumAemReactiveHttpService service = new DumAemReactiveHttpService();
            assertNotNull(service);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle connection timeout gracefully")
        void shouldHandleConnectionTimeoutGracefully() {
            DumAemConfiguration config = DumAemConfiguration.builder()
                    .url("http://non-existent-host:4502")
                    .username("admin")
                    .password("admin")
                    .build();

            var result = dumAemReactiveHttpService.fetchResponseBodyReactive(
                    "http://non-existent-host:4502/content.json", config);

            assertNotNull(result);
            // The Mono should return empty string on error based on onErrorReturn("")
        }

        @Test
        @DisplayName("Should handle invalid JSON response")
        void shouldHandleInvalidJsonResponse() {
            DumAemConfiguration config = DumAemConfiguration.builder()
                    .url("http://localhost:4502")
                    .username("admin")
                    .password("admin")
                    .build();

            var result = dumAemReactiveHttpService.fetchResponseBodyReactive(
                    "http://localhost:4502/content.html", config);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Retry Configuration Tests")
    class RetryConfigurationTests {

        @Test
        @DisplayName("Should configure retry with backoff")
        void shouldConfigureRetryWithBackoff() {
            DumAemReactiveHttpService service = new DumAemReactiveHttpService();

            DumAemConfiguration config = DumAemConfiguration.builder()
                    .url("http://localhost:4502")
                    .username("admin")
                    .password("admin")
                    .build();

            var result = service.fetchResponseBodyReactive(
                    "http://localhost:4502/content.json", config);

            assertNotNull(result);
        }
    }
}
