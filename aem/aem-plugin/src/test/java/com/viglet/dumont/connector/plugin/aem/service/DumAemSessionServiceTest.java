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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemContentMapping;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemModel;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.plugin.aem.api.DumAemPathList;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;

@ExtendWith(MockitoExtension.class)
@DisplayName("DumAemSessionService Tests")
class DumAemSessionServiceTest {

    @Mock
    private DumAemSourceService dumAemSourceService;

    @Mock
    private DumAemContentDefinitionService dumAemContentDefinitionService;

    @Mock
    private DumAemContentMappingService dumAemContentMappingService;

    private DumAemSessionService service;

    @BeforeEach
    void setUp() {
        service = new DumAemSessionService(
                dumAemSourceService,
                dumAemContentDefinitionService,
                dumAemContentMappingService);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create service with dependencies")
        void shouldCreateServiceWithDependencies() {
            assertNotNull(service);
        }
    }

    @Nested
    @DisplayName("getDumAemSession with PathList Tests")
    class GetDumAemSessionWithPathListTests {

        @Test
        @DisplayName("Should create session with path list")
        void shouldCreateSessionWithPathList() {
            // Given
            DumAemSource source = createDumAemSource();
            DumAemPathList pathList = createDumAemPathList(DumAemEvent.PUBLISHING, true);

            setupMocks(source);

            // When
            DumAemSession result = service.getDumAemSession(source, pathList, true);

            // Then
            assertNotNull(result);
            assertTrue(result.isStandalone());
        }

        @Test
        @DisplayName("Should set event from path list")
        void shouldSetEventFromPathList() {
            // Given
            DumAemSource source = createDumAemSource();
            DumAemPathList pathList = createDumAemPathList(DumAemEvent.PUBLISHING, true);

            setupMocks(source);

            // When
            DumAemSession result = service.getDumAemSession(source, pathList, false);

            // Then
            assertNotNull(result);
            assertEquals(DumAemEvent.PUBLISHING, result.getEvent());
        }

        @Test
        @DisplayName("Should set indexChildren from path list recursive flag")
        void shouldSetIndexChildrenFromPathListRecursiveFlag() {
            // Given
            DumAemSource source = createDumAemSource();
            DumAemPathList pathList = createDumAemPathList(DumAemEvent.NONE, false);

            setupMocks(source);

            // When
            DumAemSession result = service.getDumAemSession(source, pathList, true);

            // Then
            assertNotNull(result);
            assertFalse(result.isIndexChildren());
        }

        @Test
        @DisplayName("Should default to NONE event when path list is null")
        void shouldDefaultToNoneEventWhenPathListIsNull() {
            // Given
            DumAemSource source = createDumAemSource();

            setupMocks(source);

            // When
            DumAemSession result = service.getDumAemSession(source, (DumAemPathList) null, true);

            // Then
            assertNotNull(result);
            assertEquals(DumAemEvent.NONE, result.getEvent());
        }

        @Test
        @DisplayName("Should default to true for indexChildren when path list is null")
        void shouldDefaultToTrueForIndexChildrenWhenPathListIsNull() {
            // Given
            DumAemSource source = createDumAemSource();

            setupMocks(source);

            // When
            DumAemSession result = service.getDumAemSession(source, (DumAemPathList) null, true);

            // Then
            assertNotNull(result);
            assertTrue(result.isIndexChildren());
        }

        @Test
        @DisplayName("Should include configuration in session")
        void shouldIncludeConfigurationInSession() {
            // Given
            DumAemSource source = createDumAemSource();
            DumAemPathList pathList = createDumAemPathList(DumAemEvent.NONE, true);
            DumAemConfiguration expectedConfig = createMockConfiguration();

            setupMocksWithConfig(source, expectedConfig);

            // When
            DumAemSession result = service.getDumAemSession(source, pathList, true);

            // Then
            assertNotNull(result);
            assertNotNull(result.getConfiguration());
        }

        @Test
        @DisplayName("Should include attribute specs in session")
        void shouldIncludeAttributeSpecsInSession() {
            // Given
            DumAemSource source = createDumAemSource();
            DumAemPathList pathList = createDumAemPathList(DumAemEvent.NONE, true);
            List<TurSNAttributeSpec> expectedSpecs = createAttributeSpecs();

            setupMocksWithAttributeSpecs(source, expectedSpecs);

            // When
            DumAemSession result = service.getDumAemSession(source, pathList, true);

            // Then
            assertNotNull(result);
            assertNotNull(result.getAttributeSpecs());
            assertEquals(expectedSpecs.size(), result.getAttributeSpecs().size());
        }

        @Test
        @DisplayName("Should include model in session when available")
        void shouldIncludeModelInSessionWhenAvailable() {
            // Given
            DumAemSource source = createDumAemSource();
            DumAemPathList pathList = createDumAemPathList(DumAemEvent.NONE, true);
            DumAemModel expectedModel = createDumAemModel();

            setupMocksWithModel(source, expectedModel);

            // When
            DumAemSession result = service.getDumAemSession(source, pathList, true);

            // Then
            assertNotNull(result);
            assertNotNull(result.getModel());
            assertEquals("cq:Page", result.getModel().getType());
        }

        @Test
        @DisplayName("Should have null model when not available")
        void shouldHaveNullModelWhenNotAvailable() {
            // Given
            DumAemSource source = createDumAemSource();
            DumAemPathList pathList = createDumAemPathList(DumAemEvent.NONE, true);

            setupMocksWithoutModel(source);

            // When
            DumAemSession result = service.getDumAemSession(source, pathList, true);

            // Then
            assertNotNull(result);
            assertNull(result.getModel());
        }
    }

    @Nested
    @DisplayName("getDumAemSession without PathList Tests")
    class GetDumAemSessionWithoutPathListTests {

        @Test
        @DisplayName("Should create session without path list")
        void shouldCreateSessionWithoutPathList() {
            // Given
            DumAemSource source = createDumAemSource();

            setupMocks(source);

            // When
            DumAemSession result = service.getDumAemSession(source, true);

            // Then
            assertNotNull(result);
            assertTrue(result.isStandalone());
        }

        @Test
        @DisplayName("Should use default values when no path list provided")
        void shouldUseDefaultValuesWhenNoPathListProvided() {
            // Given
            DumAemSource source = createDumAemSource();

            setupMocks(source);

            // When
            DumAemSession result = service.getDumAemSession(source, false);

            // Then
            assertNotNull(result);
            assertEquals(DumAemEvent.NONE, result.getEvent());
            assertTrue(result.isIndexChildren());
            assertFalse(result.isStandalone());
        }
    }

    private DumAemSource createDumAemSource() {
        return DumAemSource.builder()
                .name("Test Source")
                .endpoint("http://localhost:4502")
                .build();
    }

    private DumAemPathList createDumAemPathList(DumAemEvent event, boolean recursive) {
        return DumAemPathList.builder()
                .event(event)
                .recursive(recursive)
                .paths(List.of("/content/test"))
                .build();
    }

    private DumAemConfiguration createMockConfiguration() {
        DumAemConfiguration config = mock(DumAemConfiguration.class);
        lenient().when(config.getContentType()).thenReturn("cq:Page");
        return config;
    }

    private DumConnectorSession createMockConnectorSession() {
        return DumConnectorSession.builder()
                .source("test-source")
                .transactionId("tx-123")
                .sites(Collections.singletonList("site1"))
                .providerName("AEM")
                .locale(Locale.ENGLISH)
                .build();
    }

    private DumAemContentMapping createContentMapping() {
        return DumAemContentMapping.builder()
                .models(new ArrayList<>())
                .targetAttrDefinitions(new ArrayList<>())
                .build();
    }

    private List<TurSNAttributeSpec> createAttributeSpecs() {
        return List.of(
                TurSNAttributeSpec.builder()
                        .name("title")
                        .type(TurSEFieldType.STRING)
                        .mandatory(true)
                        .build());
    }

    private DumAemModel createDumAemModel() {
        return DumAemModel.builder()
                .type("cq:Page")
                .className("com.example.PageClass")
                .targetAttrs(new ArrayList<>())
                .build();
    }

    private void setupMocks(DumAemSource source) {
        DumAemContentMapping contentMapping = createContentMapping();
        DumConnectorSession connectorSession = createMockConnectorSession();
        DumAemConfiguration config = createMockConfiguration();

        when(dumAemContentMappingService.getDumAemContentMapping(source)).thenReturn(contentMapping);
        when(dumAemContentDefinitionService.getAttributeSpec(contentMapping)).thenReturn(new ArrayList<>());
        when(dumAemSourceService.getDumConnectorSession(source)).thenReturn(connectorSession);
        when(dumAemSourceService.getDumAemConfiguration(source)).thenReturn(config);
        when(dumAemContentDefinitionService.getModel(any(), any())).thenReturn(Optional.empty());
    }

    private void setupMocksWithConfig(DumAemSource source, DumAemConfiguration config) {
        DumAemContentMapping contentMapping = createContentMapping();
        DumConnectorSession connectorSession = createMockConnectorSession();

        when(dumAemContentMappingService.getDumAemContentMapping(source)).thenReturn(contentMapping);
        when(dumAemContentDefinitionService.getAttributeSpec(contentMapping)).thenReturn(new ArrayList<>());
        when(dumAemSourceService.getDumConnectorSession(source)).thenReturn(connectorSession);
        when(dumAemSourceService.getDumAemConfiguration(source)).thenReturn(config);
        when(dumAemContentDefinitionService.getModel(any(), any())).thenReturn(Optional.empty());
    }

    private void setupMocksWithAttributeSpecs(DumAemSource source, List<TurSNAttributeSpec> specs) {
        DumAemContentMapping contentMapping = createContentMapping();
        DumConnectorSession connectorSession = createMockConnectorSession();
        DumAemConfiguration config = createMockConfiguration();

        when(dumAemContentMappingService.getDumAemContentMapping(source)).thenReturn(contentMapping);
        when(dumAemContentDefinitionService.getAttributeSpec(contentMapping)).thenReturn(specs);
        when(dumAemSourceService.getDumConnectorSession(source)).thenReturn(connectorSession);
        when(dumAemSourceService.getDumAemConfiguration(source)).thenReturn(config);
        when(dumAemContentDefinitionService.getModel(any(), any())).thenReturn(Optional.empty());
    }

    private void setupMocksWithModel(DumAemSource source, DumAemModel model) {
        DumAemContentMapping contentMapping = createContentMapping();
        DumConnectorSession connectorSession = createMockConnectorSession();
        DumAemConfiguration config = createMockConfiguration();

        when(dumAemContentMappingService.getDumAemContentMapping(source)).thenReturn(contentMapping);
        when(dumAemContentDefinitionService.getAttributeSpec(contentMapping)).thenReturn(new ArrayList<>());
        when(dumAemSourceService.getDumConnectorSession(source)).thenReturn(connectorSession);
        when(dumAemSourceService.getDumAemConfiguration(source)).thenReturn(config);
        when(dumAemContentDefinitionService.getModel(config, source)).thenReturn(Optional.of(model));
    }

    private void setupMocksWithoutModel(DumAemSource source) {
        DumAemContentMapping contentMapping = createContentMapping();
        DumConnectorSession connectorSession = createMockConnectorSession();
        DumAemConfiguration config = createMockConfiguration();

        when(dumAemContentMappingService.getDumAemContentMapping(source)).thenReturn(contentMapping);
        when(dumAemContentDefinitionService.getAttributeSpec(contentMapping)).thenReturn(new ArrayList<>());
        when(dumAemSourceService.getDumConnectorSession(source)).thenReturn(connectorSession);
        when(dumAemSourceService.getDumAemConfiguration(source)).thenReturn(config);
        when(dumAemContentDefinitionService.getModel(config, source)).thenReturn(Optional.empty());
    }
}
