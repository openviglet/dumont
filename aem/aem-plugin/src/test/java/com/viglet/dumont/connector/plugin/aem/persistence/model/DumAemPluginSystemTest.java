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

package com.viglet.dumont.connector.plugin.aem.persistence.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DumAemPluginSystem Tests")
class DumAemPluginSystemTest {

    @Nested
    @DisplayName("No Args Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create instance with no args constructor")
        void shouldCreateInstanceWithNoArgsConstructor() {
            DumAemPluginSystem system = new DumAemPluginSystem();

            assertNotNull(system);
            assertNull(system.getConfig());
            assertNull(system.getStringValue());
            assertFalse(system.isBooleanValue());
        }
    }

    @Nested
    @DisplayName("String Value Constructor Tests")
    class StringValueConstructorTests {

        @Test
        @DisplayName("Should create instance with string value constructor")
        void shouldCreateInstanceWithStringValueConstructor() {
            DumAemPluginSystem system = new DumAemPluginSystem("config-key", "string-value");

            assertEquals("config-key", system.getConfig());
            assertEquals("string-value", system.getStringValue());
            assertFalse(system.isBooleanValue());
        }

        @Test
        @DisplayName("Should handle null string value")
        void shouldHandleNullStringValue() {
            DumAemPluginSystem system = new DumAemPluginSystem("config-key", (String) null);

            assertEquals("config-key", system.getConfig());
            assertNull(system.getStringValue());
        }
    }

    @Nested
    @DisplayName("Boolean Value Constructor Tests")
    class BooleanValueConstructorTests {

        @Test
        @DisplayName("Should create instance with true boolean value")
        void shouldCreateInstanceWithTrueBooleanValue() {
            DumAemPluginSystem system = new DumAemPluginSystem("config-key", true);

            assertEquals("config-key", system.getConfig());
            assertTrue(system.isBooleanValue());
            assertNull(system.getStringValue());
        }

        @Test
        @DisplayName("Should create instance with false boolean value")
        void shouldCreateInstanceWithFalseBooleanValue() {
            DumAemPluginSystem system = new DumAemPluginSystem("config-key", false);

            assertEquals("config-key", system.getConfig());
            assertFalse(system.isBooleanValue());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get config")
        void shouldSetAndGetConfig() {
            DumAemPluginSystem system = new DumAemPluginSystem();
            system.setConfig("test-config");

            assertEquals("test-config", system.getConfig());
        }

        @Test
        @DisplayName("Should set and get stringValue")
        void shouldSetAndGetStringValue() {
            DumAemPluginSystem system = new DumAemPluginSystem();
            system.setStringValue("test-string-value");

            assertEquals("test-string-value", system.getStringValue());
        }

        @Test
        @DisplayName("Should set and get booleanValue")
        void shouldSetAndGetBooleanValue() {
            DumAemPluginSystem system = new DumAemPluginSystem();
            system.setBooleanValue(true);

            assertTrue(system.isBooleanValue());
        }
    }

    @Nested
    @DisplayName("Fluent Setter Tests (Accessors chain)")
    class FluentSetterTests {

        @Test
        @DisplayName("Should support fluent setters")
        void shouldSupportFluentSetters() {
            DumAemPluginSystem system = new DumAemPluginSystem()
                    .setConfig("config")
                    .setStringValue("value")
                    .setBooleanValue(true);

            assertEquals("config", system.getConfig());
            assertEquals("value", system.getStringValue());
            assertTrue(system.isBooleanValue());
        }
    }

    @Nested
    @DisplayName("Serializable Tests")
    class SerializableTests {

        @Test
        @DisplayName("Should be serializable")
        void shouldBeSerializable() throws Exception {
            DumAemPluginSystem system = new DumAemPluginSystem("config", "value");
            system.setBooleanValue(true);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(system);
            oos.close();

            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bis);
            DumAemPluginSystem deserialized = (DumAemPluginSystem) ois.readObject();

            assertEquals(system.getConfig(), deserialized.getConfig());
            assertEquals(system.getStringValue(), deserialized.getStringValue());
            assertEquals(system.isBooleanValue(), deserialized.isBooleanValue());
        }
    }
}
