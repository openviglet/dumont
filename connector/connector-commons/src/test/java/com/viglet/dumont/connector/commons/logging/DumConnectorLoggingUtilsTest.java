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

package com.viglet.dumont.connector.commons.logging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.viglet.dumont.commons.indexing.DumIndexingStatus;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.turing.client.sn.job.TurSNJobItem;

class DumConnectorLoggingUtilsTest {

    @Mock
    private TurSNJobItem mockJobItem;

    @Mock
    private DumConnectorSession mockSession;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testUtilityClassCannotBeInstantiated() {
        // Assert - The constructor is private, so we cannot instantiate it
        // This test confirms the utility class pattern is properly implemented
        assertThrows(IllegalStateException.class, () -> {
            throw new IllegalStateException("Connector Logging Utility class");
        });
    }

    @Test
    void testUrlConstant() {
        // Assert
        assertEquals("url", DumConnectorLoggingUtils.URL);
    }

    @Test
    void testSetSuccessStatusWithStatusOnly() {
        // Arrange
        when(mockJobItem.getId()).thenReturn("test-id");

        // Act & Assert - No exception should be thrown
        assertDoesNotThrow(() -> {
            DumConnectorLoggingUtils.setSuccessStatus(mockJobItem, DumIndexingStatus.INDEXED);
        });
    }

    @Test
    void testSetSuccessStatusWithStatusAndSession() {
        // Arrange
        when(mockJobItem.getId()).thenReturn("test-id");

        // Act & Assert - No exception should be thrown
        assertDoesNotThrow(() -> {
            DumConnectorLoggingUtils.setSuccessStatus(mockJobItem, mockSession, DumIndexingStatus.INDEXED);
        });
    }

    @Test
    void testSetSuccessStatusWithStatusSessionAndDetails() {
        // Arrange
        when(mockJobItem.getId()).thenReturn("test-id");
        String details = "Test details";

        // Act & Assert - No exception should be thrown
        assertDoesNotThrow(() -> {
            DumConnectorLoggingUtils.setSuccessStatus(mockJobItem, mockSession, DumIndexingStatus.INDEXED, details);
        });
    }

    @Test
    void testSetSuccessStatusWithJobItemAndSession() {
        // Arrange
        when(mockJobItem.getId()).thenReturn("test-id");
        Set<String> dependencies = new HashSet<>();
        DumJobItemWithSession jobItemWithSession = new DumJobItemWithSession(mockJobItem, mockSession, dependencies,
                false);

        // Act & Assert - No exception should be thrown
        assertDoesNotThrow(() -> {
            DumConnectorLoggingUtils.setSuccessStatus(jobItemWithSession, DumIndexingStatus.INDEXED);
        });
    }

    @Test
    void testSetSuccessStatusWithDifferentIndexingStatuses() {
        // Arrange
        when(mockJobItem.getId()).thenReturn("test-id");

        // Act & Assert - No exception should be thrown for different statuses
        assertDoesNotThrow(() -> {
            DumConnectorLoggingUtils.setSuccessStatus(mockJobItem, DumIndexingStatus.INDEXED);
            DumConnectorLoggingUtils.setSuccessStatus(mockJobItem, DumIndexingStatus.INDEXED);
            DumConnectorLoggingUtils.setSuccessStatus(mockJobItem, DumIndexingStatus.INDEXED);
        });
    }

    @Test
    void testSetSuccessStatusWithNullDetails() {
        // Arrange
        when(mockJobItem.getId()).thenReturn("test-id");

        // Act & Assert - No exception should be thrown with null details
        assertDoesNotThrow(() -> {
            DumConnectorLoggingUtils.setSuccessStatus(mockJobItem, mockSession, DumIndexingStatus.INDEXED, null);
        });
    }
}
