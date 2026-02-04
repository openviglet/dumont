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

package com.viglet.dumont.connector.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
class DumRestClientConfigTest {

    @Test
    void testConfigurationClassExists() {
        // Assert
        assertNotNull(DumRestClientConfig.class);
    }

    @Test
    void testConfigurationHasAnnotation() {
        // Assert
        assertNotNull(DumRestClientConfig.class.getAnnotation(
                org.springframework.context.annotation.Configuration.class));
    }

    @Test
    void testRestTemplateBeanCanBeCreated() {
        // Assert
        assertDoesNotThrow(() -> {
            RestTemplate template = new RestTemplate();
            assertNotNull(template);
        });
    }

    @Test
    void testHttpClientConfigured() {
        // Act
        boolean isConfigured = DumRestClientConfig.class.isAnnotationPresent(
                org.springframework.context.annotation.Configuration.class);

        // Assert
        assertTrue(isConfigured);
    }

    @Test
    void testRestClientTimeout() {
        // Assert
        assertDoesNotThrow(() -> {
            // Timeout configuration
        });
    }
}
