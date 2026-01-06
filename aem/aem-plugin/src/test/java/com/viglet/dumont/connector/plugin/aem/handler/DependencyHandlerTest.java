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

package com.viglet.dumont.connector.plugin.aem.handler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.plugin.aem.command.IndexingCommand;
import com.viglet.dumont.connector.plugin.aem.command.IndexingCommandFactory;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.service.DumAemService;

@ExtendWith(MockitoExtension.class)
@DisplayName("DependencyHandler Tests")
class DependencyHandlerTest {

    @Mock
    private DumConnectorContext connectorContext;

    @Mock
    private DumAemService aemService;

    @Mock
    private IndexingCommandFactory commandFactory;

    @Mock
    private DumAemSession session;

    @Mock
    private IndexingCommand indexingCommand;

    private DependencyHandler dependencyHandler;

    @BeforeEach
    void setUp() {
        dependencyHandler = new DependencyHandler(
                connectorContext,
                aemService,
                commandFactory,
                true);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create handler with dependencies enabled")
        void shouldCreateHandlerWithDependenciesEnabled() {
            DependencyHandler handler = new DependencyHandler(
                    connectorContext, aemService, commandFactory, true);

            assertNotNull(handler);
            assertTrue(handler.isDependenciesEnabled());
        }

        @Test
        @DisplayName("Should create handler with dependencies disabled")
        void shouldCreateHandlerWithDependenciesDisabled() {
            DependencyHandler handler = new DependencyHandler(
                    connectorContext, aemService, commandFactory, false);

            assertNotNull(handler);
            assertFalse(handler.isDependenciesEnabled());
        }
    }

    @Nested
    @DisplayName("isDependenciesEnabled Tests")
    class IsDependenciesEnabledTests {

        @Test
        @DisplayName("Should return true when enabled")
        void shouldReturnTrueWhenEnabled() {
            assertTrue(dependencyHandler.isDependenciesEnabled());
        }

        @Test
        @DisplayName("Should return false when disabled")
        void shouldReturnFalseWhenDisabled() {
            DependencyHandler handler = new DependencyHandler(
                    connectorContext, aemService, commandFactory, false);

            assertFalse(handler.isDependenciesEnabled());
        }
    }

    @Nested
    @DisplayName("processDependencies Tests")
    class ProcessDependenciesTests {

        @Test
        @DisplayName("Should not process when dependencies are disabled")
        void shouldNotProcessWhenDisabled() {
            DependencyHandler handler = new DependencyHandler(
                    connectorContext, aemService, commandFactory, false);

            handler.processDependencies(session, List.of("/content/test"));

            verify(connectorContext, never()).getObjectIdByDependency(any(), any(), any());
        }

        @Test
        @DisplayName("Should not process when paths are null")
        void shouldNotProcessWhenPathsNull() {
            dependencyHandler.processDependencies(session, null);

            verify(connectorContext, never()).getObjectIdByDependency(any(), any(), any());
        }

        @Test
        @DisplayName("Should not process when paths are empty")
        void shouldNotProcessWhenPathsEmpty() {
            dependencyHandler.processDependencies(session, Collections.emptyList());

            verify(connectorContext, never()).getObjectIdByDependency(any(), any(), any());
        }

        @Test
        @DisplayName("Should not create command when no dependencies found")
        void shouldNotCreateCommandWhenNoDependencies() {
            List<String> paths = List.of("/content/test/page");
            when(session.getSource()).thenReturn("test-source");
            when(aemService.getProviderName()).thenReturn("AEM");
            when(connectorContext.getObjectIdByDependency(anyString(), anyString(), anyList()))
                    .thenReturn(Collections.emptyList());

            dependencyHandler.processDependencies(session, paths);

            verify(commandFactory, never()).createIndexPathsCommand(any(), any());
        }

        @Test
        @DisplayName("Should create and execute command when dependencies found")
        void shouldCreateAndExecuteCommandWhenDependenciesFound() {
            List<String> paths = List.of("/content/test/page");
            List<String> dependentIds = List.of("dependent-1", "dependent-2");

            when(session.getSource()).thenReturn("test-source");
            when(aemService.getProviderName()).thenReturn("AEM");
            when(connectorContext.getObjectIdByDependency(anyString(), anyString(), anyList()))
                    .thenReturn(dependentIds);
            when(commandFactory.createIndexPathsCommand(session, dependentIds))
                    .thenReturn(indexingCommand);

            dependencyHandler.processDependencies(session, paths);

            verify(commandFactory).createIndexPathsCommand(session, dependentIds);
            verify(indexingCommand).execute();
        }

        @Test
        @DisplayName("Should process multiple paths")
        void shouldProcessMultiplePaths() {
            List<String> paths = List.of(
                    "/content/test/page1",
                    "/content/test/page2",
                    "/content/test/page3");
            List<String> dependentIds = List.of("dependent-1");

            when(session.getSource()).thenReturn("test-source");
            when(aemService.getProviderName()).thenReturn("AEM");
            when(connectorContext.getObjectIdByDependency(anyString(), anyString(), anyList()))
                    .thenReturn(dependentIds);
            when(commandFactory.createIndexPathsCommand(session, dependentIds))
                    .thenReturn(indexingCommand);

            assertDoesNotThrow(() -> dependencyHandler.processDependencies(session, paths));

            verify(connectorContext).getObjectIdByDependency("test-source", "AEM", paths);
        }
    }
}
