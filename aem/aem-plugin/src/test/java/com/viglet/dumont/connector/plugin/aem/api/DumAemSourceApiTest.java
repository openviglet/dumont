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
import com.viglet.dumont.connector.plugin.aem.mapper.DumAemSourceMapper;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSourceLocalePath;
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
    private DumAemPluginProcess dumAemPluginProcess;

    private DumAemSourceMapper dumAemSourceMapper;

    private DumAemSourceApi api;

    @BeforeEach
    void setUp() {
        dumAemSourceMapper = Mappers.getMapper(DumAemSourceMapper.class);
        api = new DumAemSourceApi(
                dumAemSourceRepository,
                dumAemSourceLocalePathRepository,
                dumAemPluginProcess,
                dumAemSourceMapper);
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
            assertEquals("Updated Name", result.getBody().getName());
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

    private DumAemSource createDumAemSource(String id) {
        return DumAemSource.builder()
                .id(id)
                .name("Test Source " + id)
                .endpoint("http://localhost:4502")
                .localePaths(new HashSet<>())
                .build();
    }

    private DumAemSource createUpdatedDumAemSource(String id) {
        return DumAemSource.builder()
                .id(id)
                .name("Updated Source " + id)
                .endpoint("http://localhost:4503")
                .localePaths(new HashSet<>())
                .build();
    }

    private DumAemSource createFullyUpdatedDumAemSource(String id) {
        return DumAemSource.builder()
                .id(id)
                .name("Updated Name")
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
