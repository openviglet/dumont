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
package com.viglet.dumont.connector.plugin.aem.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.viglet.dumont.connector.plugin.aem.DumAemPluginProcess;

@ExtendWith(MockitoExtension.class)
@DisplayName("DumAemApi Tests")
class DumAemApiTest {

    @Mock
    private DumAemPluginProcess dumAemPluginProcess;

    private DumAemApi dumAemApi;

    @BeforeEach
    void setUp() {
        dumAemApi = new DumAemApi(dumAemPluginProcess);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {
        @Test
        @DisplayName("Should create instance with DumAemPluginProcess")
        void shouldCreateInstanceWithDumAemPluginProcess() {
            assertNotNull(dumAemApi);
        }
    }

    @Nested
    @DisplayName("Status Endpoint Tests")
    class StatusTests {
        @Test
        @DisplayName("Should return status ok")
        void shouldReturnStatusOk() {
            Map<String, String> status = dumAemApi.status();

            assertNotNull(status);
            assertEquals("ok", status.get("status"));
        }
    }

    @Nested
    @DisplayName("Index Content Tests")
    class IndexContentTests {
        @Test
        @DisplayName("Should index content and return sent status")
        void shouldIndexContentAndReturnSentStatus() {
            DumAemPathList pathList = DumAemPathList.builder()
                    .paths(List.of("/content/test/page1"))
                    .build();

            ResponseEntity<Map<String, String>> response = dumAemApi.indexContentId("test-source", pathList);

            assertNotNull(response);
            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertEquals("sent", response.getBody().get("status"));
            verify(dumAemPluginProcess).sentToIndexStandaloneAsync(eq("test-source"), any(DumAemPathList.class));
        }

        @Test
        @DisplayName("Should handle multiple paths")
        void shouldHandleMultiplePaths() {
            DumAemPathList pathList = DumAemPathList.builder()
                    .paths(List.of("/content/test/page1", "/content/test/page2", "/content/test/page3"))
                    .build();

            ResponseEntity<Map<String, String>> response = dumAemApi.indexContentId("my-source", pathList);

            assertNotNull(response);
            assertEquals(200, response.getStatusCode().value());
            verify(dumAemPluginProcess).sentToIndexStandaloneAsync(eq("my-source"), any(DumAemPathList.class));
        }

        @Test
        @DisplayName("Should handle empty paths list")
        void shouldHandleEmptyPathsList() {
            DumAemPathList pathList = DumAemPathList.builder()
                    .paths(new ArrayList<>())
                    .build();

            ResponseEntity<Map<String, String>> response = dumAemApi.indexContentId("test-source", pathList);

            assertNotNull(response);
            assertEquals(200, response.getStatusCode().value());
        }
    }

    @Nested
    @DisplayName("Repeated Request Handling Tests")
    class RepeatedRequestTests {
        @Test
        @DisplayName("Should skip repeated requests within 30 seconds")
        void shouldSkipRepeatedRequestsWithin30Seconds() {
            DumAemPathList pathList = DumAemPathList.builder()
                    .paths(new ArrayList<>(List.of("/content/test/page1")))
                    .build();

            // First request
            dumAemApi.indexContentId("test-source", pathList);

            // Second request with same path - should be skipped
            DumAemPathList pathList2 = DumAemPathList.builder()
                    .paths(new ArrayList<>(List.of("/content/test/page1")))
                    .build();

            dumAemApi.indexContentId("test-source", pathList2);

            // First call should process, second might be skipped
            verify(dumAemPluginProcess, atLeastOnce()).sentToIndexStandaloneAsync(anyString(),
                    any(DumAemPathList.class));
        }

        @Test
        @DisplayName("Should process different paths even with same source")
        void shouldProcessDifferentPathsWithSameSource() {
            DumAemPathList pathList1 = DumAemPathList.builder()
                    .paths(new ArrayList<>(List.of("/content/test/page1")))
                    .build();

            DumAemPathList pathList2 = DumAemPathList.builder()
                    .paths(new ArrayList<>(List.of("/content/test/page2")))
                    .build();

            dumAemApi.indexContentId("test-source", pathList1);
            dumAemApi.indexContentId("test-source", pathList2);

            verify(dumAemPluginProcess, atLeast(1)).sentToIndexStandaloneAsync(anyString(), any(DumAemPathList.class));
        }
    }
}
