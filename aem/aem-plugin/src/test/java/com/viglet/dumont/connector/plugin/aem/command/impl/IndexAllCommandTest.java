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

package com.viglet.dumont.connector.plugin.aem.command.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.plugin.aem.command.IndexingCommand;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.navigator.AemNodeNavigator;

@DisplayName("IndexAllCommand Tests")
@ExtendWith(MockitoExtension.class)
class IndexAllCommandTest {

    @Mock
    private AemNodeNavigator nodeNavigator;

    private DumAemSession session;
    private IndexAllCommand command;

    @BeforeEach
    void setUp() {
        DumAemConfiguration configuration = DumAemConfiguration.builder()
                .rootPath("/content/mysite")
                .contentType("cq:Page")
                .url("http://localhost:4502")
                .build();

        session = DumAemSession.builder()
                .source("test-source-id")
                .configuration(configuration)
                .build();

        command = new IndexAllCommand(session, nodeNavigator);
    }

    @Nested
    @DisplayName("Interface Implementation Tests")
    class InterfaceImplementationTests {

        @Test
        @DisplayName("Should implement IndexingCommand interface")
        void shouldImplementIndexingCommandInterface() {
            assertTrue(command instanceof IndexingCommand);
        }
    }

    @Nested
    @DisplayName("getSession Tests")
    class GetSessionTests {

        @Test
        @DisplayName("Should return session")
        void shouldReturnSession() {
            assertNotNull(command.getSession());
            assertEquals(session, command.getSession());
        }

        @Test
        @DisplayName("Should return source from session")
        void shouldReturnSourceFromSession() {
            assertEquals("test-source-id", command.getSession().getSource());
        }
    }

    @Nested
    @DisplayName("getDescription Tests")
    class GetDescriptionTests {

        @Test
        @DisplayName("Should return formatted description")
        void shouldReturnFormattedDescription() {
            String description = command.getDescription();

            assertNotNull(description);
            assertTrue(description.startsWith("IndexAll[source="));
        }
    }
}
