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

package com.viglet.dumont.connector.commons;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DumConnectorSessionTest {

    private DumConnectorSession session;
    private Collection<String> sites;
    private static final String SOURCE = "testSource";
    private static final String PROVIDER_NAME = "testProvider";

    @BeforeEach
    void setUp() {
        sites = Arrays.asList("site1", "site2");
        session = new DumConnectorSession(SOURCE, sites, PROVIDER_NAME, Locale.US);
    }

    @Test
    void testConstructorInitializesFields() {
        // Arrange & Act - already done in setUp
        
        // Assert
        assertEquals(SOURCE, session.getSource());
        assertEquals(PROVIDER_NAME, session.getProviderName());
        assertEquals(sites, session.getSites());
        assertEquals(Locale.US, session.getLocale());
    }

    @Test
    void testConstructorGeneratesTransactionId() {
        // Arrange & Act
        DumConnectorSession session1 = new DumConnectorSession(SOURCE, sites, PROVIDER_NAME, Locale.US);
        DumConnectorSession session2 = new DumConnectorSession(SOURCE, sites, PROVIDER_NAME, Locale.US);
        
        // Assert
        assertNotNull(session1.getTransactionId());
        assertNotNull(session2.getTransactionId());
        assertNotEquals(session1.getTransactionId(), session2.getTransactionId());
    }

    @Test
    void testConstructorWithEmptySites() {
        // Arrange
        Collection<String> emptySites = Arrays.asList();
        
        // Act
        DumConnectorSession emptySession = new DumConnectorSession(SOURCE, emptySites, PROVIDER_NAME, Locale.US);
        
        // Assert
        assertEquals(emptySites, emptySession.getSites());
        assertTrue(emptySession.getSites().isEmpty());
    }

    @Test
    void testSettersWork() {
        // Arrange
        String newSource = "newSource";
        String newProvider = "newProvider";
        Collection<String> newSites = Arrays.asList("site3");
        Locale newLocale = Locale.FRANCE;
        String newTransactionId = "test-transaction-id";
        
        // Act
        session.setSource(newSource);
        session.setProviderName(newProvider);
        session.setSites(newSites);
        session.setLocale(newLocale);
        session.setTransactionId(newTransactionId);
        
        // Assert
        assertEquals(newSource, session.getSource());
        assertEquals(newProvider, session.getProviderName());
        assertEquals(newSites, session.getSites());
        assertEquals(newLocale, session.getLocale());
        assertEquals(newTransactionId, session.getTransactionId());
    }

    @Test
    void testBuilderPattern() {
        // Arrange
        String builderSource = "builderSource";
        String builderId = "builder-id";
        Collection<String> builderSites = Arrays.asList("site1", "site2", "site3");
        String builderProvider = "builderProvider";
        Locale builderLocale = Locale.GERMAN;
        
        // Act
        DumConnectorSession builtSession = DumConnectorSession.builder()
            .source(builderSource)
            .transactionId(builderId)
            .sites(builderSites)
            .providerName(builderProvider)
            .locale(builderLocale)
            .build();
        
        // Assert
        assertEquals(builderSource, builtSession.getSource());
        assertEquals(builderId, builtSession.getTransactionId());
        assertEquals(builderSites, builtSession.getSites());
        assertEquals(builderProvider, builtSession.getProviderName());
        assertEquals(builderLocale, builtSession.getLocale());
    }

    @Test
    void testConstructorWithNullLocale() {
        // Arrange & Act
        DumConnectorSession nullLocaleSession = new DumConnectorSession(SOURCE, sites, PROVIDER_NAME, null);
        
        // Assert
        assertNull(nullLocaleSession.getLocale());
        assertNotNull(nullLocaleSession.getTransactionId());
    }
}
