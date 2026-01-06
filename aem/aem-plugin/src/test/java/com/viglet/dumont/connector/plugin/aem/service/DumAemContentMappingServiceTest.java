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
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.aem.commons.mappers.DumAemContentMapping;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemModel;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemAttributeSpecification;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemPluginModel;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSourceAttribute;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemTargetAttribute;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemAttributeSpecificationRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemPluginModelRepository;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;

@ExtendWith(MockitoExtension.class)
@DisplayName("DumAemContentMappingService Tests")
class DumAemContentMappingServiceTest {

    @Mock
    private DumAemPluginModelRepository dumAemPluginModelRepository;

    @Mock
    private DumAemAttributeSpecificationRepository dumAemAttributeSpecificationRepository;

    private DumAemContentMappingService service;

    @BeforeEach
    void setUp() {
        service = new DumAemContentMappingService(
                dumAemPluginModelRepository,
                dumAemAttributeSpecificationRepository);
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
    @DisplayName("getDumAemContentMapping Tests")
    class GetDumAemContentMappingTests {

        @Test
        @DisplayName("Should return content mapping with models")
        void shouldReturnContentMappingWithModels() {
            // Given
            DumAemSource source = createDumAemSource();
            List<DumAemPluginModel> models = createPluginModels(source);

            when(dumAemPluginModelRepository.findByDumAemSource(source)).thenReturn(models);
            when(dumAemAttributeSpecificationRepository.findByDumAemSource(source))
                    .thenReturn(Optional.empty());

            // When
            DumAemContentMapping result = service.getDumAemContentMapping(source);

            // Then
            assertNotNull(result);
            assertNotNull(result.getModels());
        }

        @Test
        @DisplayName("Should return content mapping with attribute specifications")
        void shouldReturnContentMappingWithAttributeSpecs() {
            // Given
            DumAemSource source = createDumAemSource();
            List<DumAemAttributeSpecification> specs = createAttributeSpecifications(source);

            when(dumAemPluginModelRepository.findByDumAemSource(source)).thenReturn(Collections.emptyList());
            when(dumAemAttributeSpecificationRepository.findByDumAemSource(source))
                    .thenReturn(Optional.of(specs));

            // When
            DumAemContentMapping result = service.getDumAemContentMapping(source);

            // Then
            assertNotNull(result);
            assertNotNull(result.getTargetAttrDefinitions());
            assertFalse(result.getTargetAttrDefinitions().isEmpty());
        }

        @Test
        @DisplayName("Should return content mapping with delta class name")
        void shouldReturnContentMappingWithDeltaClassName() {
            // Given
            DumAemSource source = createDumAemSource();
            source.setDeltaClass("com.example.DeltaClass");

            when(dumAemPluginModelRepository.findByDumAemSource(source)).thenReturn(Collections.emptyList());
            when(dumAemAttributeSpecificationRepository.findByDumAemSource(source))
                    .thenReturn(Optional.empty());

            // When
            DumAemContentMapping result = service.getDumAemContentMapping(source);

            // Then
            assertNotNull(result);
            assertEquals("com.example.DeltaClass", result.getDeltaClassName());
        }

        @Test
        @DisplayName("Should return empty models when none exist")
        void shouldReturnEmptyModelsWhenNoneExist() {
            // Given
            DumAemSource source = createDumAemSource();

            when(dumAemPluginModelRepository.findByDumAemSource(source)).thenReturn(Collections.emptyList());
            when(dumAemAttributeSpecificationRepository.findByDumAemSource(source))
                    .thenReturn(Optional.empty());

            // When
            DumAemContentMapping result = service.getDumAemContentMapping(source);

            // Then
            assertNotNull(result);
            assertTrue(result.getModels().isEmpty());
        }

        @Test
        @DisplayName("Should map target attributes correctly")
        void shouldMapTargetAttributesCorrectly() {
            // Given
            DumAemSource source = createDumAemSource();
            List<DumAemPluginModel> models = createPluginModelsWithTargetAttrs(source);

            when(dumAemPluginModelRepository.findByDumAemSource(source)).thenReturn(models);
            when(dumAemAttributeSpecificationRepository.findByDumAemSource(source))
                    .thenReturn(Optional.empty());

            // When
            DumAemContentMapping result = service.getDumAemContentMapping(source);

            // Then
            assertNotNull(result);
            assertFalse(result.getModels().isEmpty());
            DumAemModel firstModel = result.getModels().get(0);
            assertNotNull(firstModel.getTargetAttrs());
        }

        @Test
        @DisplayName("Should map attribute specification to TurSNAttributeSpec")
        void shouldMapAttributeSpecToTurSNAttributeSpec() {
            // Given
            DumAemSource source = createDumAemSource();
            List<DumAemAttributeSpecification> specs = createDetailedAttributeSpecifications(source);

            when(dumAemPluginModelRepository.findByDumAemSource(source)).thenReturn(Collections.emptyList());
            when(dumAemAttributeSpecificationRepository.findByDumAemSource(source))
                    .thenReturn(Optional.of(specs));

            // When
            DumAemContentMapping result = service.getDumAemContentMapping(source);

            // Then
            assertNotNull(result);
            List<TurSNAttributeSpec> targetAttrDefs = result.getTargetAttrDefinitions();
            assertFalse(targetAttrDefs.isEmpty());
            TurSNAttributeSpec spec = targetAttrDefs.get(0);
            assertEquals("title", spec.getName());
            assertEquals("com.example.TitleClass", spec.getClassName());
            assertTrue(spec.isMandatory());
        }
    }

    private DumAemSource createDumAemSource() {
        return DumAemSource.builder()
                .name("Test Source")
                .endpoint("http://localhost:4502")
                .build();
    }

    private List<DumAemPluginModel> createPluginModels(DumAemSource source) {
        DumAemPluginModel model = DumAemPluginModel.builder()
                .type("cq:Page")
                .className("com.example.PageClass")
                .dumAemSource(source)
                .targetAttrs(new HashSet<>())
                .build();
        return List.of(model);
    }

    private List<DumAemPluginModel> createPluginModelsWithTargetAttrs(DumAemSource source) {
        DumAemTargetAttribute targetAttr = DumAemTargetAttribute.builder()
                .name("title")
                .sourceAttrs(new HashSet<>())
                .build();

        DumAemSourceAttribute sourceAttr = DumAemSourceAttribute.builder()
                .name("jcr:title")
                .className("com.example.SourceClass")
                .dumAemTargetAttribute(targetAttr)
                .build();

        targetAttr.setSourceAttrs(Set.of(sourceAttr));

        DumAemPluginModel model = DumAemPluginModel.builder()
                .type("cq:Page")
                .className("com.example.PageClass")
                .dumAemSource(source)
                .targetAttrs(Set.of(targetAttr))
                .build();

        return List.of(model);
    }

    private List<DumAemAttributeSpecification> createAttributeSpecifications(DumAemSource source) {
        DumAemAttributeSpecification spec = DumAemAttributeSpecification.builder()
                .name("title")
                .className("com.example.Class")
                .type(TurSEFieldType.STRING)
                .dumAemSource(source)
                .build();
        return List.of(spec);
    }

    private List<DumAemAttributeSpecification> createDetailedAttributeSpecifications(DumAemSource source) {
        DumAemAttributeSpecification spec = DumAemAttributeSpecification.builder()
                .name("title")
                .className("com.example.TitleClass")
                .type(TurSEFieldType.STRING)
                .mandatory(true)
                .multiValued(false)
                .facet(true)
                .description("Title attribute")
                .dumAemSource(source)
                .build();
        return List.of(spec);
    }
}
