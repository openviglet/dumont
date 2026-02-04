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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DumSNSiteTest {

    @Mock
    private DumSEInstance mockSEInstance;

    private DumSNSite site;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        site = new DumSNSite();
    }

    @Test
    void testSettersAndGetters() {
        // Arrange
        String siteName = "test-site";

        // Act
        site.setName(siteName);
        site.setDumSEInstance(mockSEInstance);

        // Assert
        assertEquals(siteName, site.getName());
        assertEquals(mockSEInstance, site.getDumSEInstance());
    }

    @Test
    void testSiteWithoutInstance() {
        // Arrange
        String siteName = "site-without-instance";

        // Act
        site.setName(siteName);

        // Assert
        assertEquals(siteName, site.getName());
        assertNull(site.getDumSEInstance());
    }

    @Test
    void testSiteNameCanBeNull() {
        // Act
        site.setName(null);

        // Assert
        assertNull(site.getName());
    }

    @Test
    void testSiteInstanceCanBeUpdated() {
        // Arrange
        site.setName("test-site");

        // Act
        site.setDumSEInstance(mockSEInstance);
        DumSEInstance newInstance = new DumSEInstance();
        site.setDumSEInstance(newInstance);

        // Assert
        assertEquals(newInstance, site.getDumSEInstance());
    }
}
