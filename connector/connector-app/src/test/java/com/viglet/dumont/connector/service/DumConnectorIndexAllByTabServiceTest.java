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

package com.viglet.dumont.connector.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.domain.DumConnectorValidateDifference;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import com.viglet.dumont.connector.plugin.DumConnectorPluginRegistry;

@ExtendWith(MockitoExtension.class)
class DumConnectorIndexAllByTabServiceTest {

    @Mock
    private DumConnectorIndexingService indexingService;
    @Mock
    private DumConnectorSolrService solrService;
    @Mock
    private DumConnectorPluginRegistry pluginRegistry;
    @Mock
    private DumConnectorPlugin plugin;

    private DumConnectorIndexAllByTabService service;

    @BeforeEach
    void setUp() {
        service = new DumConnectorIndexAllByTabService(indexingService, solrService, pluginRegistry);
        when(pluginRegistry.resolveBySource("wknd")).thenReturn(plugin);
        when(plugin.getProviderName()).thenReturn("turing");
    }

    @Test
    void unprocessedTabIndexesDiscoveredIds() {
        when(indexingService.getUnprocessedBySourceAndProvider("wknd", "turing"))
                .thenReturn(List.of(DumConnectorIndexingModel.builder().objectId("o1").build()));

        service.indexAllByTabAsync("wknd", "unprocessed");

        verify(plugin).indexById(eq("wknd"), anyList());
    }

    @Test
    void missingTabIndexesValidationMisses() {
        when(solrService.validateContent("wknd", "turing")).thenReturn(
                DumConnectorValidateDifference.builder()
                        .missing(Map.of("core", List.of("o1", "o2"))).build());

        service.indexAllByTabAsync("wknd", "missing");

        verify(plugin).indexById(eq("wknd"), anyList());
    }

    @Test
    void extraTabIndexesValidationExtras() {
        when(solrService.validateContent("wknd", "turing")).thenReturn(
                DumConnectorValidateDifference.builder()
                        .extra(Map.of("core", List.of("o3"))).build());

        service.indexAllByTabAsync("wknd", "extra");

        verify(plugin).indexById(eq("wknd"), anyList());
    }

    @Test
    void unknownTabIndexesNothing() {
        service.indexAllByTabAsync("wknd", "bogus");
        verify(plugin, never()).indexById(any(), any());
    }
}
