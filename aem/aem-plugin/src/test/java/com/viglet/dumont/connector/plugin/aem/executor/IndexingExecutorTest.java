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

package com.viglet.dumont.connector.plugin.aem.executor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.plugin.aem.command.IndexingCommand;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;

@DisplayName("IndexingExecutor Tests")
@ExtendWith(MockitoExtension.class)
class IndexingExecutorTest {

    @Mock
    private DumConnectorContext connectorContext;

    @Mock
    private IndexingCommand command;

    @Mock
    private DumAemSession session;

    private IndexingExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new IndexingExecutor(connectorContext);

        lenient().when(session.getSource()).thenReturn("test-source-id");
        lenient().when(command.getSession()).thenReturn(session);
        lenient().when(command.getDescription()).thenReturn("TestCommand");
    }

    @Nested
    @DisplayName("executeExclusive Tests")
    class ExecuteExclusiveTests {

        @Test
        @DisplayName("Should execute command successfully")
        void shouldExecuteCommandSuccessfully() {
            doNothing().when(command).execute();

            boolean result = executor.executeExclusive(command);

            assertTrue(result);
            verify(command).execute();
        }

        @Test
        @DisplayName("Should return false when source is already processing")
        void shouldReturnFalseWhenSourceIsAlreadyProcessing() {
            // First execution - start but don't complete
            doAnswer(invocation -> {
                // Attempt second execution while first is running
                boolean secondResult = executor.executeExclusive(command);
                assertFalse(secondResult);
                return null;
            }).when(command).execute();

            executor.executeExclusive(command);
        }
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create instance with connector context")
        void shouldCreateInstanceWithConnectorContext() {
            IndexingExecutor newExecutor = new IndexingExecutor(connectorContext);

            assertNotNull(newExecutor);
        }
    }
}
