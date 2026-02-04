/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.persistence.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DumConnectorConfigVarModelTest {

    private DumConnectorConfigVarModel configVar;

    @BeforeEach
    void setUp() {
        configVar = new DumConnectorConfigVarModel();
    }

    @Test
    void testSettersAndGetters() {
        // Arrange
        String id = "config-var-1";
        String path = "test.path";
        String value = "test-value";

        // Act
        configVar.setId(id);
        configVar.setPath(path);
        configVar.setValue(value);

        // Assert
        assertEquals(id, configVar.getId());
        assertEquals(path, configVar.getPath());
        assertEquals(value, configVar.getValue());
    }

    @Test
    void testIdCannotBeNull() {
        // Arrange & Act
        configVar.setId("valid-id");

        // Assert
        assertNotNull(configVar.getId());
    }

    @Test
    void testPathCanBeNull() {
        // Act
        configVar.setPath(null);

        // Assert
        assertNull(configVar.getPath());
    }

    @Test
    void testValueCanBeNull() {
        // Act
        configVar.setValue(null);

        // Assert
        assertNull(configVar.getValue());
    }

    @Test
    void testUpdateValue() {
        // Arrange
        configVar.setId("config-id");
        configVar.setPath("config.path");
        configVar.setValue("old-value");

        // Act
        configVar.setValue("new-value");

        // Assert
        assertEquals("new-value", configVar.getValue());
    }

    @Test
    void testUpdatePath() {
        // Arrange
        configVar.setPath("old.path");

        // Act
        configVar.setPath("new.path");

        // Assert
        assertEquals("new.path", configVar.getPath());
    }
}
