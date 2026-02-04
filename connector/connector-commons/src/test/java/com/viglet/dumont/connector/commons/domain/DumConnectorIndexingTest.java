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
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.commons.indexing.DumIndexingStatus;

class DumConnectorIndexingTest {

    private DumConnectorIndexing indexing;
    private Date testDate;
    private List<String> testSites;

    @BeforeEach
    void setUp() {
        testDate = new Date();
        testSites = Arrays.asList("site1", "site2");
        indexing = DumConnectorIndexing.builder()
                .id(1)
                .objectId("test-object-id")
                .source("test-source")
                .environment("test-env")
                .transactionId("test-transaction-id")
                .checksum("test-checksum")
                .locale(Locale.US)
                .created(testDate)
                .modificationDate(testDate)
                .status(DumIndexingStatus.INDEXED)
                .sites(testSites)
                .build();
    }

    @Test
    void testBuilderCreatesValidObject() {
        // Assert
        assertEquals(1, indexing.getId());
        assertEquals("test-object-id", indexing.getObjectId());
        assertEquals("test-source", indexing.getSource());
        assertEquals("test-env", indexing.getEnvironment());
        assertEquals("test-transaction-id", indexing.getTransactionId());
        assertEquals("test-checksum", indexing.getChecksum());
        assertEquals(Locale.US, indexing.getLocale());
        assertEquals(testDate, indexing.getCreated());
        assertEquals(testDate, indexing.getModificationDate());
        assertEquals(DumIndexingStatus.INDEXED, indexing.getStatus());
        assertEquals(testSites, indexing.getSites());
    }

    @Test
    void testSettersWork() {
        // Arrange
        int newId = 2;
        String newObjectId = "new-object-id";
        String newSource = "new-source";

        // Act
        indexing.setId(newId);
        indexing.setObjectId(newObjectId);
        indexing.setSource(newSource);

        // Assert
        assertEquals(newId, indexing.getId());
        assertEquals(newObjectId, indexing.getObjectId());
        assertEquals(newSource, indexing.getSource());
    }

    @Test
    void testBuilderWithMinimalFields() {
        // Arrange & Act
        DumConnectorIndexing minimal = DumConnectorIndexing.builder()
                .objectId("minimal-object")
                .build();

        // Assert
        assertEquals("minimal-object", minimal.getObjectId());
        assertEquals(0, minimal.getId());
        assertNull(minimal.getSource());
        assertNull(minimal.getStatus());
    }

    @Test
    void testBuilderWithNullValues() {
        // Arrange & Act
        DumConnectorIndexing withNulls = DumConnectorIndexing.builder()
                .objectId("test")
                .locale(null)
                .sites(null)
                .status(null)
                .build();

        // Assert
        assertEquals("test", withNulls.getObjectId());
        assertNull(withNulls.getLocale());
        assertNull(withNulls.getSites());
        assertNull(withNulls.getStatus());
    }

    @Test
    void testSettersWithDifferentLocales() {
        // Arrange
        Locale frenchLocale = Locale.FRANCE;

        // Act
        indexing.setLocale(frenchLocale);

        // Assert
        assertEquals(frenchLocale, indexing.getLocale());
    }

    @Test
    void testSettersWithDifferentStatus() {
        // Arrange & Act
        indexing.setStatus(DumIndexingStatus.INDEXED);

        // Assert
        assertEquals(DumIndexingStatus.INDEXED, indexing.getStatus());
    }

    @Test
    void testBuilderWithEmptySitesList() {
        // Arrange & Act
        List<String> emptySites = Arrays.asList();
        DumConnectorIndexing emptyIndexing = DumConnectorIndexing.builder()
                .objectId("test")
                .sites(emptySites)
                .build();

        // Assert
        assertTrue(emptyIndexing.getSites().isEmpty());
    }

    @Test
    void testBuilderWithMultipleSites() {
        // Arrange & Act
        List<String> multipleSites = Arrays.asList("site1", "site2", "site3", "site4");
        DumConnectorIndexing multiIndexing = DumConnectorIndexing.builder()
                .objectId("test")
                .sites(multipleSites)
                .build();

        // Assert
        assertEquals(4, multiIndexing.getSites().size());
        assertTrue(multiIndexing.getSites().contains("site1"));
        assertTrue(multiIndexing.getSites().contains("site4"));
    }
}
