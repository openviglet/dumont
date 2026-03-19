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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DumConnectorSessionTest {

    private DumConnectorSession session;
    private static final String SOURCE = "testSource";
    private static final String PROVIDER_NAME = "testProvider";

    @BeforeEach
    void setUp() {
        session = new DumConnectorSession(SOURCE, PROVIDER_NAME, Locale.US);
    }

    @Test
    void testConstructorInitializesFields() {
        assertEquals(SOURCE, session.getSource());
        assertEquals(PROVIDER_NAME, session.getProviderName());
        assertEquals(Locale.US, session.getLocale());
    }

    @Test
    void testConstructorGeneratesTransactionId() {
        DumConnectorSession session1 = new DumConnectorSession(SOURCE, PROVIDER_NAME, Locale.US);
        DumConnectorSession session2 = new DumConnectorSession(SOURCE, PROVIDER_NAME, Locale.US);

        assertNotNull(session1.getTransactionId());
        assertNotNull(session2.getTransactionId());
        assertNotEquals(session1.getTransactionId(), session2.getTransactionId());
    }

    @Test
    void testBuilderPattern() {
        String builderSource = "builderSource";
        String builderId = "builder-id";
        String builderProvider = "builderProvider";
        Locale builderLocale = Locale.GERMAN;

        DumConnectorSession builtSession = DumConnectorSession.builder()
                .source(builderSource)
                .transactionId(builderId)
                .providerName(builderProvider)
                .locale(builderLocale)
                .build();

        assertEquals(builderSource, builtSession.getSource());
        assertEquals(builderId, builtSession.getTransactionId());
        assertEquals(builderProvider, builtSession.getProviderName());
        assertEquals(builderLocale, builtSession.getLocale());
    }

    @Test
    void testConstructorWithNullLocale() {
        DumConnectorSession nullLocaleSession = new DumConnectorSession(SOURCE, PROVIDER_NAME, null);

        assertNull(nullLocaleSession.getLocale());
        assertNotNull(nullLocaleSession.getTransactionId());
    }
}
