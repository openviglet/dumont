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

package com.viglet.dumont.connector.plugin.aem.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumPreviewSnapshotStore;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenanceHistoryStore;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.service.DumAemObjectService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSessionService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSourceService;
import com.viglet.dumont.connector.plugin.aem.source.DumAemStructuredSourceProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("DumAemPlugin Tests (structured-source SPI, D21)")
class DumAemPluginTest {

    @Mock
    private DumConnectorContext dumConnectorContext;
    @Mock
    private DumAemStructuredSourceProvider provider;
    @Mock
    private DumAemSourceService dumAemSourceService;
    @Mock
    private DumAemSessionService dumAemSessionService;
    @Mock
    private DumAemObjectService dumAemObjectService;
    @Mock
    private DumPreviewSnapshotStore previewSnapshotStore;
    @Mock
    private DumCoverageHistoryStore coverageHistoryStore;
    @Mock
    private DumProvenanceHistoryStore provenanceHistoryStore;

    private DumAemPlugin dumAemPlugin;

    @BeforeEach
    void setUp() {
        dumAemPlugin = new DumAemPlugin(dumConnectorContext, provider, dumAemSourceService,
                dumAemSessionService, dumAemObjectService, previewSnapshotStore, coverageHistoryStore,
                provenanceHistoryStore);
    }

    @Test
    @DisplayName("Should report the AEM provider name")
    void shouldReportProviderName() {
        when(provider.getProviderName()).thenReturn("AEM");

        assertNotNull(dumAemPlugin);
        assertEquals("AEM", dumAemPlugin.getProviderName());
    }

    @Test
    @DisplayName("indexAll should be a no-op when the source is not found")
    void shouldNoOpWhenSourceNotFound() {
        when(dumAemSourceService.getDumAemSourceByName("missing")).thenReturn(Optional.empty());

        dumAemPlugin.indexAll("missing");

        verify(dumConnectorContext, never()).finishIndexing(org.mockito.ArgumentMatchers.any(
                DumConnectorSession.class), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("indexAll should not index a source with no enabled environment")
    void shouldNotIndexSourceWithoutEnabledEnvironment() {
        DumAemSource source = DumAemSource.builder().name("no-env").build();
        when(dumAemSourceService.getDumAemSourceByName("no-env")).thenReturn(Optional.of(source));
        when(provider.enabledEnvironments(source)).thenReturn(List.of());

        dumAemPlugin.indexAll("no-env");

        verify(dumConnectorContext, never()).finishIndexing(org.mockito.ArgumentMatchers.any(
                DumConnectorSession.class), org.mockito.ArgumentMatchers.anyBoolean());
    }
}
