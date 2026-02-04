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

package com.viglet.dumont.connector.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DumSEInstanceTest {

    private DumSEInstance instance;

    @BeforeEach
    void setUp() {
        instance = new DumSEInstance();
    }

    @Test
    void testSettersAndGetters() {
        // Arrange
        String host = "localhost";
        int port = 9200;

        // Act
        instance.setHost(host);
        instance.setPort(port);

        // Assert
        assertEquals(host, instance.getHost());
        assertEquals(port, instance.getPort());
    }

    @Test
    void testPortWithDefaultValue() {
        // Act - port should be 0 by default
        
        // Assert
        assertEquals(0, instance.getPort());
    }

    @Test
    void testHostCanBeNull() {
        // Act
        instance.setHost(null);

        // Assert
        assertNull(instance.getHost());
    }

    @Test
    void testPortWithHighValue() {
        // Arrange
        int highPort = 65535;

        // Act
        instance.setPort(highPort);

        // Assert
        assertEquals(highPort, instance.getPort());
    }

    @Test
    void testPortWithLowValue() {
        // Arrange
        int lowPort = 1;

        // Act
        instance.setPort(lowPort);

        // Assert
        assertEquals(lowPort, instance.getPort());
    }

    @Test
    void testUpdateHostAndPort() {
        // Arrange
        instance.setHost("server1");
        instance.setPort(8080);

        // Act
        instance.setHost("server2");
        instance.setPort(9000);

        // Assert
        assertEquals("server2", instance.getHost());
        assertEquals(9000, instance.getPort());
    }
}
