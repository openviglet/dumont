/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.aem.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;

@ExtendWith(MockitoExtension.class)
@DisplayName("DumAemStructuredSourceProvider Tests (D21)")
class DumAemStructuredSourceProviderTest {

    private DumAemStructuredSourceProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DumAemStructuredSourceProvider(null, null, null, null, null, null, false);
    }

    @Test
    @DisplayName("reports the AEM provider name")
    void providerName() {
        assertEquals("AEM", provider.getProviderName());
    }

    @Test
    @DisplayName("enabledEnvironments lists author first, then publish, only when configured")
    void enabledEnvironments() {
        assertEquals(List.of(DumAemEnv.AUTHOR, DumAemEnv.PUBLISHING),
                provider.enabledEnvironments(DumAemSource.builder()
                        .author(true).authorSNSite("a").publish(true).publishSNSite("p").build()));
        assertEquals(List.of(DumAemEnv.AUTHOR),
                provider.enabledEnvironments(DumAemSource.builder()
                        .author(true).authorSNSite("a").publish(true).build()));
        assertEquals(List.of(DumAemEnv.PUBLISHING),
                provider.enabledEnvironments(DumAemSource.builder()
                        .publish(true).publishSNSite("p").build()));
        assertTrue(provider.enabledEnvironments(DumAemSource.builder().author(true).build()).isEmpty());
    }
}
