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

package com.viglet.dumont.connector.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.repository.CrudRepository;

@SpringBootTest
class DumConnectorDependencyRepositoryTest {

    @Test
    void testRepositoryIsInterface() {
        // Assert
        assertTrue(DumConnectorDependencyRepository.class.isInterface());
    }

    @Test
    void testRepositoryExtendsCrudRepository() {
        // Assert
        assertTrue(CrudRepository.class.isAssignableFrom(DumConnectorDependencyRepository.class));
    }

    @Test
    void testRepositoryExists() {
        // Assert - simply verify it can be loaded
        assertNotNull(DumConnectorDependencyRepository.class.getName());
    }
}
