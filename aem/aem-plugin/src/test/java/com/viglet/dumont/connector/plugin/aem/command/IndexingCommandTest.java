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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;

@DisplayName("IndexingCommand Tests")
class IndexingCommandTest {

    @Nested
    @DisplayName("Interface Contract Tests")
    class InterfaceContractTests {

        @Test
        @DisplayName("Should define execute method")
        void shouldDefineExecuteMethod() throws NoSuchMethodException {
            assertNotNull(IndexingCommand.class.getMethod("execute"));
        }

        @Test
        @DisplayName("Should define getSession method")
        void shouldDefineGetSessionMethod() throws NoSuchMethodException {
            assertNotNull(IndexingCommand.class.getMethod("getSession"));
            assertEquals(DumAemSession.class, IndexingCommand.class.getMethod("getSession").getReturnType());
        }

        @Test
        @DisplayName("Should define getDescription method")
        void shouldDefineGetDescriptionMethod() throws NoSuchMethodException {
            assertNotNull(IndexingCommand.class.getMethod("getDescription"));
            assertEquals(String.class, IndexingCommand.class.getMethod("getDescription").getReturnType());
        }
    }

    @Nested
    @DisplayName("Implementation Tests")
    class ImplementationTests {

        @Test
        @DisplayName("Should implement IndexingCommand interface")
        void shouldImplementIndexingCommandInterface() {
            IndexingCommand command = new IndexingCommand() {
                @Override
                public void execute() {
                    // Test implementation
                }

                @Override
                public DumAemSession getSession() {
                    return null;
                }

                @Override
                public String getDescription() {
                    return "Test Command";
                }
            };

            assertNotNull(command);
            assertEquals("Test Command", command.getDescription());
        }
    }
}
