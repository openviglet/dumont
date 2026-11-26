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

package com.viglet.dumont.connector.plugin.aem.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.viglet.dumont.connector.plugin.aem.DumAemPluginProcess;
import com.viglet.dumont.connector.plugin.aem.export.DumAemExchangeProcess;
import com.viglet.dumont.connector.plugin.aem.mapper.DumAemSourceMapper;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSourceLocalePath;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemAttributeSpecificationRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemPluginModelRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemSourceLocalePathRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemSourceRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("DumAemSourceApi Tests")
class DumAemSourceApiTest {

    @Mock
    private DumAemSourceRepository dumAemSourceRepository;

    @Mock
    private DumAemSourceLocalePathRepository dumAemSourceLocalePathRepository;

    @Mock
    private DumAemAttributeSpecificationRepository dumAemAttributeSpecificationRepository;

    @Mock
    private DumAemPluginModelRepository dumAemPluginModelRepository;

    @Mock
    private DumAemPluginProcess dumAemPluginProcess;

    @Mock
    private DumAemExchangeProcess dumAemExchangeProcess;

    private DumAemSourceMapper dumAemSourceMapper;

    private DumAemSourceApi api;

    @BeforeEach
    void setUp() {
        dumAemSourceMapper = Mappers.getMapper(DumAemSourceMapper.class);
        api = new DumAemSourceApi(
                dumAemSourceRepository,
                dumAemSourceLocalePathRepository,
                dumAemAttributeSpecificationRepository,
                dumAemPluginModelRepository,
                dumAemPluginProcess,
                dumAemSourceMapper,
                dumAemExchangeProcess);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create API with dependencies")
        void shouldCreateApiWithDependencies() {
            assertNotNull(api);
        }
    }

    @Nested
    @DisplayName("dumAemSourceList Tests")
    class DumAemSourceListTests {

        @Test
        @DisplayName("Should return list of all sources")
        void shouldReturnListOfAllSources() {
            // Given
            List<DumAemSource> sources = createDumAemSources();
            when(dumAemSourceRepository.findAll()).thenReturn(sources);

            // When
            List<DumAemSource> result = api.dumAemSourceList();

            // Then
            assertNotNull(result);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Should return empty list when no sources exist")
        void shouldReturnEmptyListWhenNoSourcesExist() {
            // Given
            when(dumAemSourceRepository.findAll()).thenReturn(Collections.emptyList());

            // When
            List<DumAemSource> result = api.dumAemSourceList();

            // Then
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("dumAemSourceStructure Tests")
    class DumAemSourceStructureTests {

        @Test
        @DisplayName("Should return new DumAemSource structure")
        void shouldReturnNewDumAemSourceStructure() {
            // When
            DumAemSource result = api.dumAemSourceStructure();

            // Then
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("dumAemSourceGet Tests")
    class DumAemSourceGetTests {

        @Test
        @DisplayName("Should return source by id")
        void shouldReturnSourceById() {
            // Given
            String sourceId = "source-1";
            DumAemSource source = createDumAemSource(sourceId);
            when(dumAemSourceRepository.findById(sourceId)).thenReturn(Optional.of(source));
            when(dumAemSourceLocalePathRepository.findByDumAemSource(source))
                    .thenReturn(Optional.empty());

            // When
            DumAemSource result = api.dumAemSourceGet(sourceId);

            // Then
            assertNotNull(result);
            assertEquals(sourceId, result.getId());
        }

        @Test
        @DisplayName("Should return source with locale paths")
        void shouldReturnSourceWithLocalePaths() {
            // Given
            String sourceId = "source-1";
            DumAemSource source = createDumAemSource(sourceId);
            Set<DumAemSourceLocalePath> localePaths = createLocalePaths(source);
            when(dumAemSourceRepository.findById(sourceId)).thenReturn(Optional.of(source));
            when(dumAemSourceLocalePathRepository.findByDumAemSource(source))
                    .thenReturn(Optional.of(localePaths));

            // When
            DumAemSource result = api.dumAemSourceGet(sourceId);

            // Then
            assertNotNull(result);
            assertNotNull(result.getLocalePaths());
        }

        @Test
        @DisplayName("Should return empty source when not found")
        void shouldReturnEmptySourceWhenNotFound() {
            // Given
            String sourceId = "non-existent";
            when(dumAemSourceRepository.findById(sourceId)).thenReturn(Optional.empty());

            // When
            DumAemSource result = api.dumAemSourceGet(sourceId);

            // Then
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("dumAemSourceUpdate Tests")
    class DumAemSourceUpdateTests {

        @Test
        @DisplayName("Should update existing source")
        void shouldUpdateExistingSource() {
            // Given
            String sourceId = "source-1";
            DumAemSource existingSource = createDumAemSource(sourceId);
            DumAemSource updatedSource = createUpdatedDumAemSource(sourceId);
            when(dumAemSourceRepository.findById(sourceId)).thenReturn(Optional.of(existingSource));
            when(dumAemSourceRepository.save(any())).thenReturn(existingSource);

            // When
            ResponseEntity<DumAemSource> result = api.dumAemSourceUpdate(sourceId, updatedSource);

            // Then
            assertNotNull(result.getBody());
            verify(dumAemSourceRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("Should return empty source when updating non-existent")
        void shouldReturnEmptySourceWhenUpdatingNonExistent() {
            // Given
            String sourceId = "non-existent";
            DumAemSource updatedSource = createUpdatedDumAemSource(sourceId);
            when(dumAemSourceRepository.findById(sourceId)).thenReturn(Optional.empty());

            // When
            ResponseEntity<DumAemSource> result = api.dumAemSourceUpdate(sourceId, updatedSource);

            // Then
            assertNull(result.getBody());
            verify(dumAemSourceRepository, times(0)).save(any());
        }

        @Test
        @DisplayName("Should update all source fields")
        void shouldUpdateAllSourceFields() {
            // Given
            String sourceId = "source-100";
            DumAemSource existingSource = createDumAemSource(sourceId);
            DumAemSource updatedSource = createFullyUpdatedDumAemSource(sourceId);
            when(dumAemSourceRepository.findById(sourceId)).thenReturn(Optional.of(existingSource));
            when(dumAemSourceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // When
            ResponseEntity<DumAemSource> result = api.dumAemSourceUpdate(sourceId, updatedSource);

            // Then
            assertNotNull(result.getBody());
            assertEquals("updated-name", result.getBody().getName());
        }
    }

    @Nested
    @DisplayName("dumAemSourceDelete Tests")
    class DumAemSourceDeleteTests {

        @Test
        @DisplayName("Should delete existing source")
        void shouldDeleteExistingSource() {
            // Given
            String sourceId = "source-1";
            DumAemSource source = createDumAemSource(sourceId);
            when(dumAemSourceRepository.findById(sourceId)).thenReturn(Optional.of(source));
            doNothing().when(dumAemSourceRepository).delete(source);

            // When
            boolean result = api.dumAemSourceDelete(sourceId);

            // Then
            assertTrue(result);
            verify(dumAemSourceRepository, times(1)).delete(source);
        }

        @Test
        @DisplayName("Should return false when deleting non-existent source")
        void shouldReturnFalseWhenDeletingNonExistentSource() {
            // Given
            String sourceId = "non-existent";
            when(dumAemSourceRepository.findById(sourceId)).thenReturn(Optional.empty());

            // When
            boolean result = api.dumAemSourceDelete(sourceId);

            // Then
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("dumAemSourceAdd Tests")
    class DumAemSourceAddTests {

        @Test
        @DisplayName("Should add new source")
        void shouldAddNewSource() {
            // Given
            DumAemSource source = createDumAemSource("new-source");
            when(dumAemSourceRepository.save(source)).thenReturn(source);

            // When
            DumAemSource result = api.dumAemSourceAdd(source);

            // Then
            assertNotNull(result);
            verify(dumAemSourceRepository, times(1)).save(source);
        }

        @Test
        @DisplayName("DU6: a name with whitespace is refused with 400 and nothing is saved")
        void shouldRefuseANameWithWhitespace() {
            DumAemSource source = createDumAemSource("new-source");
            source.setName("wknd site");

            org.springframework.web.server.ResponseStatusException refused = org.junit.jupiter.api.Assertions
                    .assertThrows(org.springframework.web.server.ResponseStatusException.class,
                            () -> api.dumAemSourceAdd(source));

            assertEquals(HttpStatus.BAD_REQUEST, refused.getStatusCode());
            verify(dumAemSourceRepository, org.mockito.Mockito.never()).save(any());
        }

        @Test
        @DisplayName("DU6: an update to a name with whitespace is refused with 400")
        void shouldRefuseAnUpdateToANameWithWhitespace() {
            DumAemSource source = createDumAemSource("source-1");
            source.setName("wknd site");

            org.junit.jupiter.api.Assertions.assertThrows(
                    org.springframework.web.server.ResponseStatusException.class,
                    () -> api.dumAemSourceUpdate(source.getId(), source));
            verify(dumAemSourceRepository, org.mockito.Mockito.never()).save(any());
        }
    }

    @Nested
    @DisplayName("sourceIndexAll Tests")
    class SourceIndexAllTests {

        @Test
        @DisplayName("Should trigger async index all")
        void shouldTriggerAsyncIndexAll() {
            // Given
            String sourceId = "source-1";
            doNothing().when(dumAemPluginProcess).indexAllByIdAsync(sourceId);

            // When
            ResponseEntity<Object> result = api.sourceIndexAll(sourceId);

            // Then
            assertNotNull(result);
            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(dumAemPluginProcess, times(1)).indexAllByIdAsync(sourceId);
        }
    }

    private List<DumAemSource> createDumAemSources() {
        return List.of(
                createDumAemSource("source-1"),
                createDumAemSource("source-2"));
    }

    @Nested
    @DisplayName("DU2: export and import")
    class ExportImportTests {

        @Test
        @DisplayName("GET /export hands back the exchange process's zip stream")
        void shouldDelegateExport() {
            jakarta.servlet.http.HttpServletResponse response = org.mockito.Mockito
                    .mock(jakarta.servlet.http.HttpServletResponse.class);
            org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody body = out -> { };
            when(dumAemExchangeProcess.exportObject(response)).thenReturn(body);

            assertSame(body, api.exportSources(response));
        }

        @Test
        @DisplayName("POST /import passes the upload to the exchange process")
        void shouldDelegateImport() {
            org.springframework.web.multipart.MultipartFile file = org.mockito.Mockito
                    .mock(org.springframework.web.multipart.MultipartFile.class);

            var result = api.importSources(file, null, false);

            verify(dumAemExchangeProcess).importFromMultipartFile(file,
                    com.viglet.dumont.connector.commons.exchange.DumSourceImportMode.ADD);
            assertEquals(200, result.getStatusCode().value());
        }

        @Test
        @DisplayName("DU9: POST /import?mode=replace imports in replace mode")
        void shouldDelegateAReplacingImport() {
            org.springframework.web.multipart.MultipartFile file = org.mockito.Mockito
                    .mock(org.springframework.web.multipart.MultipartFile.class);

            api.importSources(file, "replace", false);

            verify(dumAemExchangeProcess).importFromMultipartFile(file,
                    com.viglet.dumont.connector.commons.exchange.DumSourceImportMode.REPLACE);
        }

        @Test
        @DisplayName("DU9: a refused import and an unknown mode answer 400")
        void shouldAnswer400ForARefusedImport() {
            org.springframework.web.multipart.MultipartFile file = org.mockito.Mockito
                    .mock(org.springframework.web.multipart.MultipartFile.class);
            org.mockito.Mockito.doThrow(new com.viglet.dumont.connector.commons.exchange.DumSourceImportException("no"))
                    .when(dumAemExchangeProcess).importFromMultipartFile(file,
                            com.viglet.dumont.connector.commons.exchange.DumSourceImportMode.REPLACE);

            var refused = org.junit.jupiter.api.Assertions.assertThrows(
                    org.springframework.web.server.ResponseStatusException.class,
                    () -> api.importSources(file, "replace", false));
            assertEquals(HttpStatus.BAD_REQUEST, refused.getStatusCode());

            var unknown = org.junit.jupiter.api.Assertions.assertThrows(
                    org.springframework.web.server.ResponseStatusException.class,
                    () -> api.importSources(file, "overwrite", false));
            assertEquals(HttpStatus.BAD_REQUEST, unknown.getStatusCode());
        }

        @Test
        @DisplayName("DU10: dryRun answers the replace plan and imports nothing")
        void shouldAnswerThePlanOnADryRun() {
            org.springframework.web.multipart.MultipartFile file = org.mockito.Mockito
                    .mock(org.springframework.web.multipart.MultipartFile.class);
            var plan = new com.viglet.dumont.connector.commons.exchange.DumSourceReplacePlan(
                    List.of("wknd"), List.of(), List.of("legacy"), List.of());
            when(dumAemExchangeProcess.planFromMultipartFile(file)).thenReturn(plan);

            var result = api.importSources(file, "replace", true);

            assertSame(plan, result.getBody());
            verify(dumAemExchangeProcess, org.mockito.Mockito.never()).importFromMultipartFile(any(), any());
        }

        @Test
        @DisplayName("DU10: dryRun without mode=replace answers 400")
        void shouldRefuseADryRunOfAnAddingImport() {
            org.springframework.web.multipart.MultipartFile file = org.mockito.Mockito
                    .mock(org.springframework.web.multipart.MultipartFile.class);

            var refused = org.junit.jupiter.api.Assertions.assertThrows(
                    org.springframework.web.server.ResponseStatusException.class,
                    () -> api.importSources(file, null, true));
            assertEquals(HttpStatus.BAD_REQUEST, refused.getStatusCode());
        }
    }

    private DumAemSource createDumAemSource(String id) {
        return DumAemSource.builder()
                .id(id)
                .name("test-source-" + id)
                .endpoint("http://localhost:4502")
                .localePaths(new HashSet<>())
                .build();
    }

    private DumAemSource createUpdatedDumAemSource(String id) {
        return DumAemSource.builder()
                .id(id)
                .name("updated-source-" + id)
                .endpoint("http://localhost:4503")
                .localePaths(new HashSet<>())
                .build();
    }

    private DumAemSource createFullyUpdatedDumAemSource(String id) {
        return DumAemSource.builder()
                .id(id)
                .name("updated-name")
                .endpoint("http://localhost:4503")
                .username("admin")
                .password("admin123")
                .authorURLPrefix("http://author")
                .publishURLPrefix("http://publish")
                .author(true)
                .publish(true)
                .oncePattern("/content/once")
                .rootPath("/content")
                .localePaths(new HashSet<>())
                .build();
    }

    private Set<DumAemSourceLocalePath> createLocalePaths(DumAemSource source) {
        DumAemSourceLocalePath localePath = DumAemSourceLocalePath.builder()
                .locale(Locale.ENGLISH)
                .path("/content/en")
                .dumAemSource(source)
                .build();
        return Set.of(localePath);
    }
}
