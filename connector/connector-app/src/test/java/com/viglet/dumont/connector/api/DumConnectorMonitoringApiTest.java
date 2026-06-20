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

package com.viglet.dumont.connector.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenanceHistoryStore;
import com.viglet.dumont.connector.domain.DumConnectorMonitoringPage;
import com.viglet.dumont.connector.domain.DumConnectorMonitoringRequest;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import com.viglet.dumont.connector.plugin.DumConnectorPluginRegistry;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.dumont.connector.service.DumCoverageHealthService;

@ExtendWith(MockitoExtension.class)
class DumConnectorMonitoringApiTest {

    @Mock
    private DumConnectorIndexingService indexingService;
    @Mock
    private DumConnectorPluginRegistry pluginRegistry;
    @Mock
    private DumCoverageHistoryStore coverageHistoryStore;
    @Mock
    private DumCoverageHealthService coverageHealthService;
    @Mock
    private DumProvenanceHistoryStore provenanceHistoryStore;

    private DumConnectorMonitoringApi api;

    @BeforeEach
    void setUp() {
        api = new DumConnectorMonitoringApi(indexingService, pluginRegistry, coverageHistoryStore,
                coverageHealthService, provenanceHistoryStore, 25);
    }

    @Test
    void monitoringUnprocessedReturnsEmptyMonitoringWhenNoneFound() {
        when(pluginRegistry.resolveProviderBySource("s")).thenReturn("turing");
        when(indexingService.getUnprocessedBySourceAndProvider("s", "turing"))
                .thenReturn(List.of());
        assertThat(api.monitoringUnprocessed("s").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void monitoringIndexingPopulatesSourcesWhenPresent() {
        when(indexingService.findAll())
                .thenReturn(List.of(DumConnectorIndexingModel.builder().objectId("o").build()));
        when(indexingService.getAllSources(null)).thenReturn(List.of("s"));
        assertThat(api.monitoringIndexing().getBody().getSources()).containsExactly("s");
    }

    @Test
    void monitoringBySourceDelegates() {
        when(pluginRegistry.resolveProviderBySource("s")).thenReturn("turing");
        when(indexingService.getBySourceAndProvider("s", "turing")).thenReturn(List.of());
        assertThat(api.monitoringIndexingBySource("s").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void monitoringItemReturnsOkOrNotFound() {
        when(indexingService.findById(1))
                .thenReturn(Optional.of(DumConnectorIndexingModel.builder().objectId("o").build()));
        when(indexingService.findById(2)).thenReturn(Optional.empty());
        assertThat(api.monitoringIndexingItem(1).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.monitoringIndexingItem(2).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void indexContentIdSearchesAcrossProviders() {
        when(indexingService.findAllByProviderAndObjectIdIn(eq(null), any())).thenReturn(List.of());
        assertThat(api.indexContentId(List.of("c1")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void searchDelegatesWithNullProvider() {
        DumConnectorMonitoringRequest request = DumConnectorMonitoringRequest.builder().build();
        DumConnectorMonitoringPage page = DumConnectorMonitoringPage.builder().build();
        when(indexingService.search(request, null)).thenReturn(page);
        assertThat(api.search(request).getBody()).isSameAs(page);
    }

    @Test
    void statsEndpointsDelegate() {
        when(indexingService.getAllStats()).thenReturn(List.of());
        assertThat(api.monitoringStats().getStatusCode()).isEqualTo(HttpStatus.OK);

        when(indexingService.countByProviderGroupBySource(null)).thenReturn(java.util.Map.of());
        assertThat(api.monitoringStatsCount().getStatusCode()).isEqualTo(HttpStatus.OK);

        when(pluginRegistry.resolveProviderBySource("s")).thenReturn("turing");
        when(indexingService.getStatsBySourceAndProvider("s", "turing")).thenReturn(List.of());
        assertThat(api.monitoringStatsBySource("s").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void coverageHistoryAndDriftNoneWhenInsufficientRuns() {
        when(coverageHistoryStore.history("s")).thenReturn(List.of());
        assertThat(api.coverageHistory("s").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.coverageDrift("s").getBody()).isNotNull();
    }

    @Test
    void coverageSummaryDelegatesToHealthService() {
        DumConnectorSummaryApi.SummaryResponse summary =
                new DumConnectorSummaryApi.SummaryResponse(true, null, "ok", false);
        when(coverageHealthService.summarize("s", 25, false)).thenReturn(summary);
        assertThat(api.coverageSummary("s", false).getBody()).isSameAs(summary);
    }

    @Test
    void provenanceHistoryAndLastSeenNoneWhenEmpty() {
        when(provenanceHistoryStore.history("s")).thenReturn(List.of());
        assertThat(api.provenanceHistory("s").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.provenanceLastSeen("s").getBody()).isNotNull();
    }
}
