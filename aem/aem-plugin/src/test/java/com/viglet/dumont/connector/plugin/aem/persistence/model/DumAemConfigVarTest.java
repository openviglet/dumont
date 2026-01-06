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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DumAemConfigVar Tests")
class DumAemConfigVarTest {

    @Nested
    @DisplayName("No Args Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create instance with no args constructor")
        void shouldCreateInstanceWithNoArgsConstructor() {
            DumAemConfigVar configVar = new DumAemConfigVar();

            assertNotNull(configVar);
            assertNull(configVar.getId());
            assertNull(configVar.getPath());
            assertNull(configVar.getValue());
        }
    }

    @Nested
    @DisplayName("All Args Constructor Tests")
    class AllArgsConstructorTests {

        @Test
        @DisplayName("Should create instance with all args constructor")
        void shouldCreateInstanceWithAllArgsConstructor() {
            DumAemConfigVar configVar = new DumAemConfigVar("config1", "/content/path", "value1");

            assertEquals("config1", configVar.getId());
            assertEquals("/content/path", configVar.getPath());
            assertEquals("value1", configVar.getValue());
        }

        @Test
        @DisplayName("Should handle null values in constructor")
        void shouldHandleNullValuesInConstructor() {
            DumAemConfigVar configVar = new DumAemConfigVar(null, null, null);

            assertNull(configVar.getId());
            assertNull(configVar.getPath());
            assertNull(configVar.getValue());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get id")
        void shouldSetAndGetId() {
            DumAemConfigVar configVar = new DumAemConfigVar();
            configVar.setId("test-id");

            assertEquals("test-id", configVar.getId());
        }

        @Test
        @DisplayName("Should set and get path")
        void shouldSetAndGetPath() {
            DumAemConfigVar configVar = new DumAemConfigVar();
            configVar.setPath("/content/mysite/en");

            assertEquals("/content/mysite/en", configVar.getPath());
        }

        @Test
        @DisplayName("Should set and get value")
        void shouldSetAndGetValue() {
            DumAemConfigVar configVar = new DumAemConfigVar();
            configVar.setValue("my-value");

            assertEquals("my-value", configVar.getValue());
        }

        @Test
        @DisplayName("Should handle large value")
        void shouldHandleLargeValue() {
            DumAemConfigVar configVar = new DumAemConfigVar();
            String largeValue = "x".repeat(10000);
            configVar.setValue(largeValue);

            assertEquals(largeValue, configVar.getValue());
        }
    }

    @Nested
    @DisplayName("Serializable Tests")
    class SerializableTests {

        @Test
        @DisplayName("Should be serializable")
        void shouldBeSerializable() throws Exception {
            DumAemConfigVar configVar = new DumAemConfigVar("config-id", "/path", "value");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(configVar);
            oos.close();

            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bis);
            DumAemConfigVar deserialized = (DumAemConfigVar) ois.readObject();

            assertEquals(configVar.getId(), deserialized.getId());
            assertEquals(configVar.getPath(), deserialized.getPath());
            assertEquals(configVar.getValue(), deserialized.getValue());
        }
    }
}
