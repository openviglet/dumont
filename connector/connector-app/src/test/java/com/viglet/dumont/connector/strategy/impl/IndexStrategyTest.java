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

package com.viglet.dumont.connector.strategy.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.HashSet;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.viglet.dumont.connector.batch.JobItemBatchProcessor;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.turing.client.sn.job.TurSNJobItem;

class IndexStrategyTest {

    @Mock
    private DumConnectorIndexingService indexingService;

    @Mock
    private JobItemBatchProcessor batchProcessor;

    @Mock
    private TurSNJobItem jobItem;

    @Mock
    private DumConnectorSession session;

    private IndexStrategy indexStrategy;
    private DumJobItemWithSession jobItemWithSession;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        indexStrategy = new IndexStrategy(indexingService);
        jobItemWithSession = new DumJobItemWithSession(jobItem, session, new HashSet<>(), false);
    }

    @Test
    void testProcessCallsIndexingService() {
        // Arrange
        when(jobItem.getId()).thenReturn("test-id");

        // Act
        indexStrategy.process(jobItemWithSession, batchProcessor);

        // Assert
        verify(indexingService).save(eq(jobItemWithSession), any());
    }

    @Test
    void testProcessCallsBatchProcessor() {
        // Arrange
        when(jobItem.getId()).thenReturn("test-id");

        // Act
        indexStrategy.process(jobItemWithSession, batchProcessor);

        // Assert
        verify(batchProcessor).add(jobItem, session);
    }

    @Test
    void testCanHandleWithValidJobItem() {
        // Arrange
        when(jobItem.getId()).thenReturn("test-id");
        when(indexingService.exists(jobItemWithSession)).thenReturn(false);

        // Act
        boolean result = indexStrategy.canHandle(jobItemWithSession);

        // Assert
        assertTrue(result);
    }

    @Test
    void testCanHandleWithEmptyId() {
        // Arrange
        when(jobItem.getId()).thenReturn("");

        // Act
        boolean result = indexStrategy.canHandle(jobItemWithSession);

        // Assert
        assertFalse(result);
    }

    @Test
    void testCanHandleWithNullId() {
        // Arrange
        when(jobItem.getId()).thenReturn(null);

        // Act
        boolean result = indexStrategy.canHandle(jobItemWithSession);

        // Assert
        assertFalse(result);
    }

    @Test
    void testCanHandleWhenIndexingExists() {
        // Arrange
        when(jobItem.getId()).thenReturn("test-id");
        when(indexingService.exists(jobItemWithSession)).thenReturn(true);

        // Act
        boolean result = indexStrategy.canHandle(jobItemWithSession);

        // Assert
        assertFalse(result);
    }

    @Test
    void testGetPriority() {
        // Act
        int priority = indexStrategy.getPriority();

        // Assert
        assertEquals(30, priority);
    }

    @Test
    void testProcessWithValidJobItem() {
        // Arrange
        when(jobItem.getId()).thenReturn("test-object-id");

        // Act & Assert
        assertDoesNotThrow(() -> {
            indexStrategy.process(jobItemWithSession, batchProcessor);
        });
    }
}
