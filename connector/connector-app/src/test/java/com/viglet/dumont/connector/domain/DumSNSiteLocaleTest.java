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

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DumSNSiteLocaleTest {

    @Mock
    private DumSNSite mockSite;

    private DumSNSiteLocale siteLocale;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        siteLocale = new DumSNSiteLocale();
    }

    @Test
    void testSettersAndGetters() {
        // Arrange
        Locale language = Locale.ENGLISH;
        String core = "test-core";

        // Act
        siteLocale.setLanguage(language);
        siteLocale.setCore(core);
        siteLocale.setDumSNSite(mockSite);

        // Assert
        assertEquals(language, siteLocale.getLanguage());
        assertEquals(core, siteLocale.getCore());
        assertEquals(mockSite, siteLocale.getDumSNSite());
    }

    @Test
    void testLanguageCanBeNull() {
        // Act
        siteLocale.setLanguage(null);

        // Assert
        assertNull(siteLocale.getLanguage());
    }

    @Test
    void testCoreCanBeNull() {
        // Act
        siteLocale.setCore(null);

        // Assert
        assertNull(siteLocale.getCore());
    }

    @Test
    void testSiteCanBeNull() {
        // Act
        siteLocale.setDumSNSite(null);

        // Assert
        assertNull(siteLocale.getDumSNSite());
    }

    @Test
    void testWithDifferentLocales() {
        // Arrange
        DumSNSiteLocale enLocale = new DumSNSiteLocale();
        DumSNSiteLocale ptLocale = new DumSNSiteLocale();

        // Act
        enLocale.setLanguage(Locale.ENGLISH);
        enLocale.setCore("en-core");
        ptLocale.setLanguage(new Locale("pt", "BR"));
        ptLocale.setCore("pt-br-core");

        // Assert
        assertEquals(Locale.ENGLISH, enLocale.getLanguage());
        assertEquals("en-core", enLocale.getCore());
        assertEquals(new Locale("pt", "BR"), ptLocale.getLanguage());
        assertEquals("pt-br-core", ptLocale.getCore());
    }

    @Test
    void testUpdateValues() {
        // Arrange
        siteLocale.setLanguage(Locale.ENGLISH);
        siteLocale.setCore("old-core");

        // Act
        siteLocale.setLanguage(Locale.FRENCH);
        siteLocale.setCore("new-core");

        // Assert
        assertEquals(Locale.FRENCH, siteLocale.getLanguage());
        assertEquals("new-core", siteLocale.getCore());
    }
}
