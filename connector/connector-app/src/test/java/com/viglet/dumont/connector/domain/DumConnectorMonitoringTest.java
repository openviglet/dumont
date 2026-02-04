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

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;

class DumConnectorMonitoringTest {

    @Mock
    private DumConnectorIndexingModel mockIndexingModel;

    private DumConnectorMonitoring monitoring;
    private List<String> sources;
    private List<DumConnectorIndexingModel> indexingModels;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        sources = Arrays.asList("source1", "source2");
        indexingModels = Arrays.asList(mockIndexingModel);
        monitoring = DumConnectorMonitoring.builder()
            .sources(sources)
            .indexing(indexingModels)
            .build();
    }

    @Test
    void testBuilderCreatesValidObject() {
        // Assert
        assertEquals(sources, monitoring.getSources());
        assertEquals(indexingModels, monitoring.getIndexing());
    }

    @Test
    void testSettersAndGetters() {
        // Arrange
        DumConnectorMonitoring newMonitoring = new DumConnectorMonitoring();
        List<String> newSources = Arrays.asList("source3", "source4");

        // Act
        newMonitoring.setSources(newSources);
        newMonitoring.setIndexing(indexingModels);

        // Assert
        assertEquals(newSources, newMonitoring.getSources());
        assertEquals(indexingModels, newMonitoring.getIndexing());
    }

    @Test
    void testBuilderWithNullValues() {
        // Arrange & Act
        DumConnectorMonitoring withNulls = DumConnectorMonitoring.builder()
            .sources(null)
            .indexing(null)
            .build();

        // Assert
        assertNull(withNulls.getSources());
        assertNull(withNulls.getIndexing());
    }

    @Test
    void testBuilderWithEmptyLists() {
        // Arrange
        List<String> emptySources = Arrays.asList();
        List<DumConnectorIndexingModel> emptyIndexing = Arrays.asList();

        // Act
        DumConnectorMonitoring emptyMonitoring = DumConnectorMonitoring.builder()
            .sources(emptySources)
            .indexing(emptyIndexing)
            .build();

        // Assert
        assertTrue(emptyMonitoring.getSources().isEmpty());
        assertTrue(emptyMonitoring.getIndexing().isEmpty());
    }

    @Test
    void testDefaultConstructor() {
        // Act
        DumConnectorMonitoring defaultMonitoring = new DumConnectorMonitoring();

        // Assert
        assertNull(defaultMonitoring.getSources());
        assertNull(defaultMonitoring.getIndexing());
    }

    @Test
    void testAllArgsConstructor() {
        // Act
        DumConnectorMonitoring allArgsMonitoring = new DumConnectorMonitoring(sources, indexingModels);

        // Assert
        assertEquals(sources, allArgsMonitoring.getSources());
        assertEquals(indexingModels, allArgsMonitoring.getIndexing());
    }

    @Test
    void testMonitoringWithMultipleSources() {
        // Arrange
        List<String> multipleSources = Arrays.asList("source1", "source2", "source3", "source4");

        // Act
        DumConnectorMonitoring multiMonitoring = DumConnectorMonitoring.builder()
            .sources(multipleSources)
            .build();

        // Assert
        assertEquals(4, multiMonitoring.getSources().size());
        assertTrue(multiMonitoring.getSources().contains("source1"));
        assertTrue(multiMonitoring.getSources().contains("source4"));
    }
}
