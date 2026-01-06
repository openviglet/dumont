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

package com.viglet.dumont.connector.plugin.aem.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.plugin.aem.command.impl.IndexAllCommand;
import com.viglet.dumont.connector.plugin.aem.command.impl.IndexPathsCommand;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.navigator.AemNodeNavigator;
import com.viglet.dumont.connector.plugin.aem.service.DumAemJobService;

@DisplayName("IndexingCommandFactory Tests")
@ExtendWith(MockitoExtension.class)
class IndexingCommandFactoryTest {

    @Mock
    private AemNodeNavigator nodeNavigator;

    @Mock
    private DumAemJobService jobService;

    private IndexingCommandFactory factory;
    private DumAemSession session;

    @BeforeEach
    void setUp() {
        factory = new IndexingCommandFactory(nodeNavigator, jobService);

        DumAemConfiguration configuration = DumAemConfiguration.builder()
                .rootPath("/content/mysite")
                .url("http://localhost:4502")
                .build();

        session = DumAemSession.builder()
                .source("test-source-id")
                .configuration(configuration)
                .build();
    }

    @Nested
    @DisplayName("createIndexAllCommand Tests")
    class CreateIndexAllCommandTests {

        @Test
        @DisplayName("Should create IndexAllCommand")
        void shouldCreateIndexAllCommand() {
            IndexingCommand command = factory.createIndexAllCommand(session);

            assertNotNull(command);
            assertTrue(command instanceof IndexAllCommand);
        }

        @Test
        @DisplayName("Should return command with correct session")
        void shouldReturnCommandWithCorrectSession() {
            IndexingCommand command = factory.createIndexAllCommand(session);

            assertEquals(session, command.getSession());
        }
    }

    @Nested
    @DisplayName("createIndexPathsCommand Tests")
    class CreateIndexPathsCommandTests {

        @Test
        @DisplayName("Should create IndexPathsCommand")
        void shouldCreateIndexPathsCommand() {
            List<String> paths = Arrays.asList("/content/path1", "/content/path2");

            IndexingCommand command = factory.createIndexPathsCommand(session, paths);

            assertNotNull(command);
            assertTrue(command instanceof IndexPathsCommand);
        }

        @Test
        @DisplayName("Should return command with correct session")
        void shouldReturnCommandWithCorrectSession() {
            List<String> paths = Arrays.asList("/content/path1");

            IndexingCommand command = factory.createIndexPathsCommand(session, paths);

            assertEquals(session, command.getSession());
        }
    }
}
