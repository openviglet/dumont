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

package com.viglet.dumont.connector.commons.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.turing.client.sn.job.TurSNJobItem;

class DumJobItemWithSessionTest {

    @Mock
    private TurSNJobItem mockJobItem;
    
    @Mock
    private DumConnectorSession mockSession;
    
    private Set<String> dependencies;
    private DumJobItemWithSession jobItemWithSession;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        dependencies = new HashSet<>(Arrays.asList("dep1", "dep2"));
        jobItemWithSession = new DumJobItemWithSession(mockJobItem, mockSession, dependencies, true);
    }

    @Test
    void testRecordAccessors() {
        // Assert
        assertNotNull(jobItemWithSession.turSNJobItem());
        assertNotNull(jobItemWithSession.session());
        assertNotNull(jobItemWithSession.dependencies());
        assertTrue(jobItemWithSession.standalone());
    }

    @Test
    void testRecordWithNullJobItem() {
        // Arrange & Act
        DumJobItemWithSession withNullItem = new DumJobItemWithSession(null, mockSession, dependencies, false);
        
        // Assert
        assertNull(withNullItem.turSNJobItem());
        assertNotNull(withNullItem.session());
    }

    @Test
    void testRecordWithNullSession() {
        // Arrange & Act
        DumJobItemWithSession withNullSession = new DumJobItemWithSession(mockJobItem, null, dependencies, true);
        
        // Assert
        assertNotNull(withNullSession.turSNJobItem());
        assertNull(withNullSession.session());
    }

    @Test
    void testRecordWithEmptyDependencies() {
        // Arrange
        Set<String> emptyDependencies = new HashSet<>();
        
        // Act
        DumJobItemWithSession withEmptyDeps = new DumJobItemWithSession(mockJobItem, mockSession, emptyDependencies, false);
        
        // Assert
        assertTrue(withEmptyDeps.dependencies().isEmpty());
        assertFalse(withEmptyDeps.standalone());
    }

    @Test
    void testRecordWithMultipleDependencies() {
        // Arrange
        Set<String> multipleDeps = new HashSet<>(Arrays.asList("dep1", "dep2", "dep3", "dep4"));
        
        // Act
        DumJobItemWithSession withMultipleDeps = new DumJobItemWithSession(mockJobItem, mockSession, multipleDeps, true);
        
        // Assert
        assertEquals(4, withMultipleDeps.dependencies().size());
        assertTrue(withMultipleDeps.dependencies().contains("dep1"));
        assertTrue(withMultipleDeps.dependencies().contains("dep4"));
    }

    @Test
    void testRecordEquality() {
        // Arrange
        Set<String> sameDependencies = new HashSet<>(Arrays.asList("dep1", "dep2"));
        DumJobItemWithSession item1 = new DumJobItemWithSession(mockJobItem, mockSession, sameDependencies, true);
        DumJobItemWithSession item2 = new DumJobItemWithSession(mockJobItem, mockSession, sameDependencies, true);
        
        // Assert
        assertEquals(item1, item2);
    }

    @Test
    void testRecordStandaloneFlag() {
        // Arrange & Act
        DumJobItemWithSession standalone = new DumJobItemWithSession(mockJobItem, mockSession, dependencies, true);
        DumJobItemWithSession notStandalone = new DumJobItemWithSession(mockJobItem, mockSession, dependencies, false);
        
        // Assert
        assertTrue(standalone.standalone());
        assertFalse(notStandalone.standalone());
    }

    @Test
    void testRecordWithNullDependencies() {
        // Arrange & Act
        DumJobItemWithSession withNullDeps = new DumJobItemWithSession(mockJobItem, mockSession, null, true);
        
        // Assert
        assertNull(withNullDeps.dependencies());
    }
}
