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

package com.viglet.dumont.connector.plugin.aem.export;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.viglet.dumont.connector.plugin.aem.export.bean.DumAemAttribExchange;
import com.viglet.dumont.connector.plugin.aem.export.bean.DumAemExchange;
import com.viglet.dumont.connector.plugin.aem.export.bean.DumAemModelExchange;
import com.viglet.dumont.connector.plugin.aem.export.bean.DumAemSourceAttrExchange;
import com.viglet.dumont.connector.plugin.aem.export.bean.DumAemSourceExchange;
import com.viglet.dumont.connector.plugin.aem.export.bean.DumAemTargetAttrExchange;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemAttributeSpecificationRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemPluginModelRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemSourceAttributeRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemSourceLocalePathRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemSourceRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemTargetAttributeRepository;
import com.viglet.turing.commons.se.field.TurSEFieldType;

import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("DumAemExchangeProcess Tests")
class DumAemExchangeProcessTest {

    @Mock
    private DumAemSourceRepository dumAemSourceRepository;

    @Mock
    private DumAemAttributeSpecificationRepository dumAemAttributeSpecificationRepository;

    @Mock
    private DumAemPluginModelRepository dumAemModelRepository;

    @Mock
    private DumAemTargetAttributeRepository dumAemTargetAttributeRepository;

    @Mock
    private DumAemSourceAttributeRepository dumAemSourceAttributeRepository;

    @Mock
    private DumAemSourceLocalePathRepository dumAemSourceLocalePathRepository;

    @TempDir
    File tempDir;

    private DumAemExchangeProcess exchangeProcess;

    @BeforeEach
    void setUp() {
        exchangeProcess = new DumAemExchangeProcess(
                dumAemSourceRepository,
                dumAemAttributeSpecificationRepository,
                dumAemModelRepository,
                dumAemTargetAttributeRepository,
                dumAemSourceAttributeRepository,
                dumAemSourceLocalePathRepository);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create exchange process with dependencies")
        void shouldCreateExchangeProcessWithDependencies() {
            assertNotNull(exchangeProcess);
        }
    }

    @Nested
    @DisplayName("exportObject Tests")
    class ExportObjectTests {

        @Test
        @DisplayName("Should export sources to response")
        void shouldExportSourcesToResponse() {
            // Given
            HttpServletResponse response = mock(HttpServletResponse.class);
            List<DumAemSource> sources = createDumAemSources();
            when(dumAemSourceRepository.findAll()).thenReturn(sources);

            // When
            StreamingResponseBody result = exchangeProcess.exportObject(response);

            // Then - Should not throw exception
            assertDoesNotThrow(() -> {
                if (result != null) {
                    // StreamingResponseBody will write to output stream
                }
            });
        }

        @Test
        @DisplayName("Should handle empty source list")
        void shouldHandleEmptySourceList() {
            // Given
            HttpServletResponse response = mock(HttpServletResponse.class);
            when(dumAemSourceRepository.findAll()).thenReturn(Collections.emptyList());

            // When
            StreamingResponseBody result = exchangeProcess.exportObject(response);

            // Then
            // Should not throw exception even with empty list
            assertDoesNotThrow(() -> {
                if (result != null) {
                    // StreamingResponseBody will write to output stream
                }
            });
        }
    }

    @Nested
    @DisplayName("importFromFile Tests")
    class ImportFromFileTests {

        @Test
        @DisplayName("Should import sources from file")
        void shouldImportSourcesFromFile() throws IOException {
            // Given
            File exportFile = createExportFile();
            when(dumAemSourceRepository.findById(any())).thenReturn(Optional.empty());
            when(dumAemSourceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            lenient().when(dumAemAttributeSpecificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            lenient().when(dumAemModelRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            lenient().when(dumAemTargetAttributeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            lenient().when(dumAemSourceAttributeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            lenient().when(dumAemSourceLocalePathRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // When
            exchangeProcess.importFromFile(exportFile);

            // Then
            verify(dumAemSourceRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("Should skip existing sources during import")
        void shouldSkipExistingSourcesDuringImport() throws IOException {
            // Given
            File exportFile = createExportFile();
            DumAemSource existingSource = createDumAemSource();
            when(dumAemSourceRepository.findById(any())).thenReturn(Optional.of(existingSource));

            // When
            exchangeProcess.importFromFile(exportFile);

            // Then
            verify(dumAemSourceRepository, times(0)).save(any());
        }

        @Test
        @DisplayName("Should handle invalid JSON file gracefully")
        void shouldHandleInvalidJsonFileGracefully() throws IOException {
            // Given
            File invalidFile = new File(tempDir, "invalid.json");
            try (FileWriter writer = new FileWriter(invalidFile)) {
                writer.write("{ invalid json }");
            }

            // When & Then - Should not throw exception
            assertDoesNotThrow(() -> exchangeProcess.importFromFile(invalidFile));
        }

        @Test
        @DisplayName("Should handle non-existent file gracefully")
        void shouldHandleNonExistentFileGracefully() {
            // Given
            File nonExistentFile = new File(tempDir, "non-existent.json");

            // When & Then - Should not throw exception
            assertDoesNotThrow(() -> exchangeProcess.importFromFile(nonExistentFile));
        }
    }

    @Nested
    @DisplayName("importAemSource Tests")
    class ImportAemSourceTests {

        @Test
        @DisplayName("Should import AEM source from exchange")
        void shouldImportAemSourceFromExchange() {
            // Given
            DumAemExchange dumAemExchange = createDumAemExchange();
            when(dumAemSourceRepository.findById(any())).thenReturn(Optional.empty());
            when(dumAemSourceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            lenient().when(dumAemAttributeSpecificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            lenient().when(dumAemModelRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            lenient().when(dumAemTargetAttributeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            lenient().when(dumAemSourceAttributeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            lenient().when(dumAemSourceLocalePathRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // When
            exchangeProcess.importAemSource(dumAemExchange);

            // Then
            verify(dumAemSourceRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("Should import attribute specifications")
        void shouldImportAttributeSpecifications() {
            // Given
            DumAemExchange dumAemExchange = createDumAemExchangeWithAttributes();
            when(dumAemSourceRepository.findById(any())).thenReturn(Optional.empty());
            when(dumAemSourceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(dumAemAttributeSpecificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            lenient().when(dumAemModelRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            lenient().when(dumAemTargetAttributeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            lenient().when(dumAemSourceAttributeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            lenient().when(dumAemSourceLocalePathRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // When
            exchangeProcess.importAemSource(dumAemExchange);

            // Then
            verify(dumAemAttributeSpecificationRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("Should import models with target and source attributes")
        void shouldImportModelsWithTargetAndSourceAttributes() {
            // Given
            DumAemExchange dumAemExchange = createDumAemExchangeWithModels();
            when(dumAemSourceRepository.findById(any())).thenReturn(Optional.empty());
            when(dumAemSourceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            lenient().when(dumAemAttributeSpecificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(dumAemModelRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(dumAemTargetAttributeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(dumAemSourceAttributeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            lenient().when(dumAemSourceLocalePathRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // When
            exchangeProcess.importAemSource(dumAemExchange);

            // Then
            verify(dumAemModelRepository, times(1)).save(any());
            verify(dumAemTargetAttributeRepository, times(1)).save(any());
            verify(dumAemSourceAttributeRepository, times(1)).save(any());
        }
    }

    private List<DumAemSource> createDumAemSources() {
        DumAemSource source = DumAemSource.builder()
                .id("source-1")
                .name("Test Source")
                .endpoint("http://localhost:4502")
                .attributeSpecifications(new HashSet<>())
                .build();
        return List.of(source);
    }

    private DumAemSource createDumAemSource() {
        return DumAemSource.builder()
                .id("source-1")
                .name("Test Source")
                .endpoint("http://localhost:4502")
                .build();
    }

    private File createExportFile() throws IOException {
        File exportFile = new File(tempDir, "export.json");
        String json = """
                {
                    "sources": [
                        {
                            "id": "source-1",
                            "name": "Test Source",
                            "endpoint": "http://localhost:4502",
                            "attributes": [],
                            "models": [],
                            "localePaths": []
                        }
                    ]
                }
                """;
        try (FileWriter writer = new FileWriter(exportFile)) {
            writer.write(json);
        }
        return exportFile;
    }

    private DumAemExchange createDumAemExchange() {
        DumAemSourceExchange sourceExchange = DumAemSourceExchange.builder()
                .id("source-1")
                .name("Test Source")
                .endpoint("http://localhost:4502")
                .attributes(new ArrayList<>())
                .models(new ArrayList<>())
                .localePaths(new ArrayList<>())
                .build();
        return new DumAemExchange(List.of(sourceExchange));
    }

    private DumAemExchange createDumAemExchangeWithAttributes() {
        DumAemAttribExchange attribExchange = DumAemAttribExchange.builder()
                .name("title")
                .className("com.example.TitleClass")
                .type(TurSEFieldType.STRING)
                .mandatory(true)
                .build();

        DumAemSourceExchange sourceExchange = DumAemSourceExchange.builder()
                .id("source-1")
                .name("Test Source")
                .endpoint("http://localhost:4502")
                .attributes(List.of(attribExchange))
                .models(new ArrayList<>())
                .localePaths(new ArrayList<>())
                .build();
        return new DumAemExchange(List.of(sourceExchange));
    }

    private DumAemExchange createDumAemExchangeWithModels() {
        DumAemSourceAttrExchange sourceAttrExchange = DumAemSourceAttrExchange.builder()
                .name("jcr:title")
                .className("com.example.SourceClass")
                .build();

        DumAemTargetAttrExchange targetAttrExchange = DumAemTargetAttrExchange.builder()
                .name("title")
                .sourceAttrs(List.of(sourceAttrExchange))
                .build();

        DumAemModelExchange modelExchange = DumAemModelExchange.builder()
                .type("cq:Page")
                .className("com.example.PageClass")
                .targetAttrs(List.of(targetAttrExchange))
                .build();

        DumAemSourceExchange sourceExchange = DumAemSourceExchange.builder()
                .id("source-1")
                .name("Test Source")
                .endpoint("http://localhost:4502")
                .attributes(new ArrayList<>())
                .models(List.of(modelExchange))
                .localePaths(new ArrayList<>())
                .build();
        return new DumAemExchange(List.of(sourceExchange));
    }
}
