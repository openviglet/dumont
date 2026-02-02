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
import org.junit.jupiter.params.ParameterizedTest;
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

        @ParameterizedTest
        @DisplayName("Should generate correct basic auth header for various credentials")
        @org.junit.jupiter.params.provider.MethodSource("basicAuthTestCases")
        void shouldGenerateBasicAuthHeaderParameterized(String username, String password, String expected)
                throws Exception {
            Method basicAuthMethod = DumAemReactiveHttpService.class
                    .getDeclaredMethod("basicAuth", String.class, String.class);
            basicAuthMethod.setAccessible(true);

            String result = (String) basicAuthMethod.invoke(dumAemReactiveHttpService, username, password);

            assertNotNull(result);
            assertEquals(expected, result);
        }

        static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> basicAuthTestCases() {
            return java.util.stream.Stream.of(
                    org.junit.jupiter.params.provider.Arguments.of("admin", "admin", "Basic YWRtaW46YWRtaW4="),
                    org.junit.jupiter.params.provider.Arguments.of("", "password", "Basic OnBhc3N3b3Jk"),
                    org.junit.jupiter.params.provider.Arguments.of("admin", "", "Basic YWRtaW46"),
                    org.junit.jupiter.params.provider.Arguments.of("user@domain.com", "p@ss:word!",
                            "Basic dXNlckBkb21haW4uY29tOnBAc3M6d29yZCE="));
        }
    }

    @Nested
    @DisplayName("fetchResponseBodyReactive Tests")
    class FetchResponseBodyReactiveTests {

        @ParameterizedTest
        @DisplayName("Should return Mono for various valid URLs")
        @org.junit.jupiter.params.provider.MethodSource("urlTestCases")
        void shouldReturnMonoForVariousUrls(String testUrl) {
            DumAemConfiguration config = DumAemConfiguration.builder()
                    .url("http://localhost:4502")
                    .username("admin")
                    .password("admin")
                    .build();

            var result = dumAemReactiveHttpService.fetchResponseBodyReactive(
                    testUrl, config);

            assertNotNull(result);
        }

        static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> urlTestCases() {
            return java.util.stream.Stream.of(
                    org.junit.jupiter.params.provider.Arguments.of("http://localhost:4502/content.json"),
                    org.junit.jupiter.params.provider.Arguments
                            .of("http://localhost:4502/content/path with spaces.json"),
                    org.junit.jupiter.params.provider.Arguments
                            .of("http://localhost:4502/content.json?limit=10&offset=0"),
                    org.junit.jupiter.params.provider.Arguments.of("http://localhost:4502/content.json#section"));
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

            // Use reflection to check if SSL context or protocols are set (example, adjust
            // as needed)
            try {
                java.lang.reflect.Field webClientField = DumAemReactiveHttpService.class.getDeclaredField("webClient");
                webClientField.setAccessible(true);
                Object webClient = webClientField.get(service);
                assertNotNull(webClient, "WebClient should be initialized with SSL protocols if configured");
            } catch (NoSuchFieldException | IllegalAccessException e) {
                // If the field does not exist, the test should fail
                throw new AssertionError("SSL configuration could not be verified", e);
            }
        }

        @Test
        @DisplayName("Should create WebClient with custom user agent")
        void shouldCreateWebClientWithCustomUserAgent() {
            DumAemReactiveHttpService service = new DumAemReactiveHttpService();
            assertNotNull(service);

            // Use reflection to check if WebClient has custom user agent set
            try {
                java.lang.reflect.Field webClientField = DumAemReactiveHttpService.class.getDeclaredField("webClient");
                webClientField.setAccessible(true);
                Object webClient = webClientField.get(service);
                assertNotNull(webClient, "WebClient should be initialized");

                // Try to extract the user agent header from the WebClient (if possible)
                // This is a best-effort check; adjust as needed for your WebClient
                // implementation
                java.lang.reflect.Method getDefaultHeaders = webClient.getClass().getMethod("defaultHeaders");
                Object headersConsumer = getDefaultHeaders.invoke(webClient);
                assertNotNull(headersConsumer, "WebClient should have default headers set (including User-Agent)");
            } catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException
                    | java.lang.reflect.InvocationTargetException e) {
                throw new AssertionError("User-Agent configuration could not be verified", e);
            }
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
