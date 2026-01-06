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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemContentMapping;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemModel;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;

@ExtendWith(MockitoExtension.class)
@DisplayName("DumAemContentDefinitionService Tests")
class DumAemContentDefinitionServiceTest {

    @Mock
    private DumAemContentMappingService dumAemContentMappingService;

    private DumAemContentDefinitionService service;

    @BeforeEach
    void setUp() {
        service = new DumAemContentDefinitionService(dumAemContentMappingService);
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
    @DisplayName("getAttributeSpec Tests")
    class GetAttributeSpecTests {

        @Test
        @DisplayName("Should return attribute specs from content mapping")
        void shouldReturnAttributeSpecsFromContentMapping() {
            // Given
            List<TurSNAttributeSpec> specs = createAttributeSpecs();
            DumAemContentMapping contentMapping = DumAemContentMapping.builder()
                    .targetAttrDefinitions(specs)
                    .build();

            // When
            List<TurSNAttributeSpec> result = service.getAttributeSpec(contentMapping);

            // Then
            assertNotNull(result);
            assertEquals(specs.size(), result.size());
        }

        @Test
        @DisplayName("Should return empty list when content mapping is null")
        void shouldReturnEmptyListWhenContentMappingIsNull() {
            // When
            List<TurSNAttributeSpec> result = service.getAttributeSpec(null);

            // Then
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list when target attr definitions is null")
        void shouldReturnEmptyListWhenTargetAttrDefinitionsIsNull() {
            // Given
            DumAemContentMapping contentMapping = DumAemContentMapping.builder()
                    .targetAttrDefinitions(null)
                    .build();

            // When
            List<TurSNAttributeSpec> result = service.getAttributeSpec(contentMapping);

            // Then
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getDeltaClassName Tests")
    class GetDeltaClassNameTests {

        @Test
        @DisplayName("Should return delta class name from content mapping")
        void shouldReturnDeltaClassNameFromContentMapping() {
            // Given
            String expectedClassName = "com.example.DeltaClass";
            DumAemContentMapping contentMapping = DumAemContentMapping.builder()
                    .deltaClassName(expectedClassName)
                    .build();

            // When
            String result = service.getDeltaClassName(contentMapping);

            // Then
            assertEquals(expectedClassName, result);
        }

        @Test
        @DisplayName("Should return null when content mapping is null")
        void shouldReturnNullWhenContentMappingIsNull() {
            // When
            String result = service.getDeltaClassName(null);

            // Then
            assertNull(result);
        }

        @Test
        @DisplayName("Should return null when delta class name is not set")
        void shouldReturnNullWhenDeltaClassNameIsNotSet() {
            // Given
            DumAemContentMapping contentMapping = DumAemContentMapping.builder().build();

            // When
            String result = service.getDeltaClassName(contentMapping);

            // Then
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("getDeltaDate Tests")
    class GetDeltaDateTests {

        @Test
        @DisplayName("Should return default delta date when no custom class")
        void shouldReturnDefaultDeltaDateWhenNoCustomClass() {
            // Given
            DumAemObject aemObject = createMockAemObject();
            DumAemConfiguration config = createMockConfiguration();
            DumAemContentMapping contentMapping = DumAemContentMapping.builder().build();

            // When
            Date result = service.getDeltaDate(aemObject, config, contentMapping);

            // Then
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should return delta date from default implementation")
        void shouldReturnDeltaDateFromDefaultImplementation() {
            // Given
            DumAemObject aemObject = createMockAemObject();
            DumAemConfiguration config = createMockConfiguration();
            DumAemContentMapping contentMapping = DumAemContentMapping.builder()
                    .deltaClassName(null)
                    .build();

            // When
            Date result = service.getDeltaDate(aemObject, config, contentMapping);

            // Then
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("getModel Tests")
    class GetModelTests {

        @Test
        @DisplayName("Should return model when content type matches")
        void shouldReturnModelWhenContentTypeMatches() {
            // Given
            DumAemSource source = createDumAemSource();
            DumAemConfiguration config = createMockConfigurationWithContentType("cq:Page");

            List<DumAemModel> models = List.of(
                    DumAemModel.builder()
                            .type("cq:Page")
                            .className("com.example.PageClass")
                            .targetAttrs(new ArrayList<>())
                            .build());

            DumAemContentMapping contentMapping = DumAemContentMapping.builder()
                    .models(models)
                    .targetAttrDefinitions(new ArrayList<>())
                    .build();

            when(dumAemContentMappingService.getDumAemContentMapping(source))
                    .thenReturn(contentMapping);

            // When
            Optional<DumAemModel> result = service.getModel(config, source);

            // Then
            assertTrue(result.isPresent());
            assertEquals("cq:Page", result.get().getType());
        }

        @Test
        @DisplayName("Should return empty when no matching model found")
        void shouldReturnEmptyWhenNoMatchingModelFound() {
            // Given
            DumAemSource source = createDumAemSource();
            DumAemConfiguration config = createMockConfigurationWithContentType("dam:Asset");

            List<DumAemModel> models = List.of(
                    DumAemModel.builder()
                            .type("cq:Page")
                            .className("com.example.PageClass")
                            .targetAttrs(new ArrayList<>())
                            .build());

            DumAemContentMapping contentMapping = DumAemContentMapping.builder()
                    .models(models)
                    .targetAttrDefinitions(new ArrayList<>())
                    .build();

            when(dumAemContentMappingService.getDumAemContentMapping(source))
                    .thenReturn(contentMapping);

            // When
            Optional<DumAemModel> result = service.getModel(config, source);

            // Then
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Should return empty when content mapping is null")
        void shouldReturnEmptyWhenContentMappingIsNull() {
            // Given
            DumAemSource source = createDumAemSource();
            DumAemConfiguration config = createMockConfigurationWithContentType("cq:Page");

            when(dumAemContentMappingService.getDumAemContentMapping(source))
                    .thenReturn(null);

            // When
            Optional<DumAemModel> result = service.getModel(config, source);

            // Then
            assertFalse(result.isPresent());
        }
    }

    private List<TurSNAttributeSpec> createAttributeSpecs() {
        return List.of(
                TurSNAttributeSpec.builder()
                        .name("title")
                        .type(TurSEFieldType.STRING)
                        .mandatory(true)
                        .build(),
                TurSNAttributeSpec.builder()
                        .name("description")
                        .type(TurSEFieldType.STRING)
                        .mandatory(false)
                        .build());
    }

    private DumAemObject createMockAemObject() {
        JSONObject jcrNode = new JSONObject();
        jcrNode.put("jcr:primaryType", "cq:Page");
        JSONObject jcrContent = new JSONObject();
        jcrContent.put("jcr:title", "Test Title");
        jcrContent.put("jcr:lastModified", "2024-01-01T00:00:00.000Z");
        jcrNode.put("jcr:content", jcrContent);
        return new DumAemObject("/content/test", jcrNode, DumAemEnv.AUTHOR);
    }

    private DumAemConfiguration createMockConfiguration() {
        DumAemConfiguration config = mock(DumAemConfiguration.class);
        lenient().when(config.getContentType()).thenReturn("cq:Page");
        return config;
    }

    private DumAemConfiguration createMockConfigurationWithContentType(String contentType) {
        DumAemConfiguration config = mock(DumAemConfiguration.class);
        lenient().when(config.getContentType()).thenReturn(contentType);
        return config;
    }

    private DumAemSource createDumAemSource() {
        return DumAemSource.builder()
                .name("Test Source")
                .endpoint("http://localhost:4502")
                .build();
    }
}
