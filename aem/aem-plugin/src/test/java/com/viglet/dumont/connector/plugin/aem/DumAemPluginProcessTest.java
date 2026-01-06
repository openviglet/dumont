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

package com.viglet.dumont.connector.plugin.aem;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;
import com.viglet.dumont.connector.plugin.aem.api.DumAemPathList;
import com.viglet.dumont.connector.plugin.aem.command.IndexingCommand;
import com.viglet.dumont.connector.plugin.aem.command.IndexingCommandFactory;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.executor.IndexingExecutor;
import com.viglet.dumont.connector.plugin.aem.handler.DependencyHandler;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSessionService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSourceService;

@ExtendWith(MockitoExtension.class)
@DisplayName("DumAemPluginProcess Tests")
class DumAemPluginProcessTest {

    @Mock
    private DumAemSourceService sourceService;

    @Mock
    private DumAemSessionService sessionService;

    @Mock
    private IndexingCommandFactory commandFactory;

    @Mock
    private IndexingExecutor executor;

    @Mock
    private DependencyHandler dependencyHandler;

    @Mock
    private DumAemSource dumAemSource;

    @Mock
    private DumAemSession dumAemSession;

    @Mock
    private IndexingCommand indexingCommand;

    private DumAemPluginProcess dumAemPluginProcess;

    @BeforeEach
    void setUp() {
        dumAemPluginProcess = new DumAemPluginProcess(
                sourceService,
                sessionService,
                commandFactory,
                executor,
                dependencyHandler);
    }

    @Nested
    @DisplayName("indexAllByNameAsync Tests")
    class IndexAllByNameAsyncTests {

        @Test
        @DisplayName("Should index all when source is found by name")
        void shouldIndexAllWhenSourceFoundByName() {
            String sourceName = "test-source";
            when(sourceService.getDumAemSourceByName(sourceName))
                    .thenReturn(Optional.of(dumAemSource));
            when(sessionService.getDumAemSession(any(DumAemSource.class), any(Boolean.class)))
                    .thenReturn(dumAemSession);
            when(commandFactory.createIndexAllCommand(any())).thenReturn(indexingCommand);

            assertDoesNotThrow(() -> dumAemPluginProcess.indexAllByNameAsync(sourceName));
        }

        @Test
        @DisplayName("Should log error when source is not found by name")
        void shouldLogErrorWhenSourceNotFoundByName() {
            String sourceName = "non-existent-source";
            when(sourceService.getDumAemSourceByName(sourceName))
                    .thenReturn(Optional.empty());

            assertDoesNotThrow(() -> dumAemPluginProcess.indexAllByNameAsync(sourceName));

            verify(sessionService, never()).getDumAemSession(any(DumAemSource.class), any(Boolean.class));
        }
    }

    @Nested
    @DisplayName("indexAllByIdAsync Tests")
    class IndexAllByIdAsyncTests {

        @Test
        @DisplayName("Should index all when source is found by ID")
        void shouldIndexAllWhenSourceFoundById() {
            String sourceId = "source-123";
            when(sourceService.getDumAemSourceById(sourceId))
                    .thenReturn(Optional.of(dumAemSource));
            when(sessionService.getDumAemSession(any(DumAemSource.class), any(Boolean.class)))
                    .thenReturn(dumAemSession);
            when(commandFactory.createIndexAllCommand(any())).thenReturn(indexingCommand);

            assertDoesNotThrow(() -> dumAemPluginProcess.indexAllByIdAsync(sourceId));
        }

        @Test
        @DisplayName("Should log error when source is not found by ID")
        void shouldLogErrorWhenSourceNotFoundById() {
            String sourceId = "non-existent-id";
            when(sourceService.getDumAemSourceById(sourceId))
                    .thenReturn(Optional.empty());

            assertDoesNotThrow(() -> dumAemPluginProcess.indexAllByIdAsync(sourceId));

            verify(sessionService, never()).getDumAemSession(any(DumAemSource.class), any(Boolean.class));
        }
    }

    @Nested
    @DisplayName("indexAll Tests")
    class IndexAllTests {

        @Test
        @DisplayName("Should create session and execute index all command")
        void shouldCreateSessionAndExecuteCommand() {
            when(sessionService.getDumAemSession(any(DumAemSource.class), any(Boolean.class)))
                    .thenReturn(dumAemSession);
            when(commandFactory.createIndexAllCommand(dumAemSession)).thenReturn(indexingCommand);

            dumAemPluginProcess.indexAll(dumAemSource);

            verify(sessionService).getDumAemSession(dumAemSource, false);
            verify(commandFactory).createIndexAllCommand(dumAemSession);
            verify(executor).executeExclusive(indexingCommand);
        }
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create instance with all dependencies")
        void shouldCreateInstanceWithAllDependencies() {
            DumAemPluginProcess process = new DumAemPluginProcess(
                    sourceService,
                    sessionService,
                    commandFactory,
                    executor,
                    dependencyHandler);

            assertNotNull(process);
        }
    }

    @Nested
    @DisplayName("sentToIndexStandalone Tests")
    class SentToIndexStandaloneTests {

        @Test
        @DisplayName("Should send paths to index in standalone mode")
        void shouldSendPathsToIndexStandalone() {
            String sourceName = "test-source";
            DumAemPathList pathList = DumAemPathList.builder()
                    .paths(List.of("/content/test/page1", "/content/test/page2"))
                    .event(DumAemEvent.NONE)
                    .recursive(false)
                    .build();

            when(sourceService.getDumAemSourceByName(sourceName))
                    .thenReturn(Optional.of(dumAemSource));
            when(sessionService.getDumAemSession(any(DumAemSource.class), any(DumAemPathList.class),
                    any(Boolean.class)))
                    .thenReturn(dumAemSession);
            when(commandFactory.createIndexPathsCommand(any(), any())).thenReturn(indexingCommand);

            dumAemPluginProcess.sentToIndexStandalone(sourceName, pathList);

            verify(sourceService).getDumAemSourceByName(sourceName);
        }

        @Test
        @DisplayName("Should not index when source not found")
        void shouldNotIndexWhenSourceNotFound() {
            String sourceName = "non-existent";
            DumAemPathList pathList = DumAemPathList.builder()
                    .paths(List.of("/content/test/page1"))
                    .event(DumAemEvent.NONE)
                    .recursive(false)
                    .build();

            when(sourceService.getDumAemSourceByName(sourceName))
                    .thenReturn(Optional.empty());

            dumAemPluginProcess.sentToIndexStandalone(sourceName, pathList);

            verify(commandFactory, never()).createIndexPathsCommand(any(), any());
        }
    }
}
