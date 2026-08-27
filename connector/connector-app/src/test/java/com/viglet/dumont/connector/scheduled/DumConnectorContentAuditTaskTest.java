/*
 * Copyright (C) 2016-2026 the original author or authors.
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

package com.viglet.dumont.connector.scheduled;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin.EnvironmentInfo;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import com.viglet.dumont.connector.plugin.DumConnectorPluginRegistry;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.dumont.connector.service.DumIndexAuditService;

@ExtendWith(MockitoExtension.class)
class DumConnectorContentAuditTaskTest {

    @Mock
    private DumConnectorPluginRegistry pluginRegistry;
    @Mock
    private DumConnectorIndexingService indexingService;
    @Mock
    private DumConnectorPlugin plugin;
    @Mock
    private DumIndexAuditService indexAuditService;

    private DumConnectorContentAuditTask task;

    @BeforeEach
    void setUp() {
        // queryBuilder disabled -> sequential audit path
        task = new DumConnectorContentAuditTask(pluginRegistry, indexingService, indexAuditService,
                false, 4);
        lenient().when(plugin.getProviderName()).thenReturn("turing");
    }

    @Test
    void auditSourceAsyncRunsTheIndexComparisonWithTheDiscoveredIdsAndCollectedSites() {
        when(pluginRegistry.resolveBySource("wknd")).thenReturn(plugin);
        when(plugin.discoverContentIds("wknd")).thenReturn(List.of("o1"));
        when(plugin.resolveLocale(eq("wknd"), any())).thenReturn(Locale.US);
        when(plugin.resolveEnvironments("wknd", "o1"))
                .thenReturn(List.of(new EnvironmentInfo("prod", List.of("site1"))));
        when(indexingService.findUnprocessedByObjectIdAndSourceAndProvider(any(), any(), any()))
                .thenReturn(List.of());

        task.auditSourceAsync("wknd");

        // D64: the third comparison runs with the sites the per-object pass collected.
        verify(indexAuditService).audit(eq("wknd"), eq("turing"), eq(Set.of("site1")),
                eq(List.of("o1")), eq(Locale.US));
    }

    @Test
    void auditSourceAsyncCreatesRecordsForActiveEnvironmentsAndRemovesStaleForEmpty() {
        when(pluginRegistry.resolveBySource("wknd")).thenReturn(plugin);
        when(plugin.discoverContentIds("wknd")).thenReturn(List.of("o1", "o2"));
        when(plugin.resolveLocale(eq("wknd"), any())).thenReturn(Locale.US);
        // o1: has an active environment with sites -> creates a NOT_PROCESSED record
        when(plugin.resolveEnvironments("wknd", "o1"))
                .thenReturn(List.of(new EnvironmentInfo("prod", List.of("site1"))));
        // o2: no environments -> stale-removal path
        when(plugin.resolveEnvironments("wknd", "o2")).thenReturn(List.of());
        when(indexingService.findUnprocessedByObjectIdAndSourceAndProvider(any(), any(), any()))
                .thenReturn(List.of());
        when(indexingService.existsByObjectIdAndSourceAndEnvironmentAndProvider(
                "o1", "wknd", "prod", "turing")).thenReturn(false);

        task.auditSourceAsync("wknd");

        verify(indexingService).createUnprocessedRecord("o1", "wknd", "turing", Locale.US, "prod",
                List.of("site1"));
        verify(indexingService).saveStats(any());
    }

    @Test
    void auditSourceAsyncSkipsExistingRecords() {
        when(pluginRegistry.resolveBySource("wknd")).thenReturn(plugin);
        when(plugin.discoverContentIds("wknd")).thenReturn(List.of("o1"));
        when(plugin.resolveLocale(eq("wknd"), any())).thenReturn(Locale.US);
        when(plugin.resolveEnvironments("wknd", "o1"))
                .thenReturn(List.of(new EnvironmentInfo("prod", List.of("site1"))));
        when(indexingService.findUnprocessedByObjectIdAndSourceAndProvider(any(), any(), any()))
                .thenReturn(List.of());
        when(indexingService.existsByObjectIdAndSourceAndEnvironmentAndProvider(
                "o1", "wknd", "prod", "turing")).thenReturn(true);

        task.auditSourceAsync("wknd");

        verify(indexingService, never()).createUnprocessedRecord(any(), any(), any(), any(), any(),
                any());
    }

    @Test
    void auditContentSkipsProvidersWithNoSources() {
        when(pluginRegistry.connectorPlugins()).thenReturn(List.of(plugin));
        when(indexingService.getAllSources("turing")).thenReturn(List.of());

        task.auditContent();

        verify(plugin, never()).discoverContentIds(any());
    }

    @Test
    void auditContentAuditsEachSource() {
        when(pluginRegistry.connectorPlugins()).thenReturn(List.of(plugin));
        when(indexingService.getAllSources("turing")).thenReturn(List.of("wknd"));
        when(plugin.discoverContentIds("wknd")).thenReturn(List.of());

        task.auditContent();

        verify(plugin).discoverContentIds("wknd");
        verify(indexingService).saveStats(any());
    }

    @Test
    void auditRemovesStaleRecordsNotInActiveEnvironments() {
        when(pluginRegistry.resolveBySource("wknd")).thenReturn(plugin);
        when(plugin.discoverContentIds("wknd")).thenReturn(List.of("o1"));
        when(plugin.resolveLocale(eq("wknd"), any())).thenReturn(Locale.US);
        when(plugin.resolveEnvironments("wknd", "o1"))
                .thenReturn(List.of(new EnvironmentInfo("prod", List.of("site1"))));
        DumConnectorIndexingModel stale = org.mockito.Mockito.mock(DumConnectorIndexingModel.class);
        when(stale.getEnvironment()).thenReturn("retired");  // absent from the active prod env
        when(indexingService.findUnprocessedByObjectIdAndSourceAndProvider(any(), any(), any()))
                .thenReturn(List.of(stale));
        when(indexingService.existsByObjectIdAndSourceAndEnvironmentAndProvider(
                "o1", "wknd", "prod", "turing")).thenReturn(true);

        task.auditSourceAsync("wknd");

        verify(indexingService).deleteAll(List.of(stale));
    }

    @Test
    void auditRemovesAllStaleWhenNoEnvironments() {
        when(pluginRegistry.resolveBySource("wknd")).thenReturn(plugin);
        when(plugin.discoverContentIds("wknd")).thenReturn(List.of("gone"));
        when(plugin.resolveLocale(eq("wknd"), any())).thenReturn(Locale.US);
        when(plugin.resolveEnvironments("wknd", "gone")).thenReturn(List.of());
        DumConnectorIndexingModel stale = org.mockito.Mockito.mock(DumConnectorIndexingModel.class);
        when(indexingService.findUnprocessedByObjectIdAndSourceAndProvider("gone", "wknd", "turing"))
                .thenReturn(List.of(stale));

        task.auditSourceAsync("wknd");

        verify(indexingService).deleteAll(List.of(stale));
    }
}
