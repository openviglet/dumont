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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.api.DumConnectorSummaryApi.SummaryResponse;
import com.viglet.dumont.connector.config.DumTuringCsrfService;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingStatsModel;
import com.viglet.dumont.connector.persistence.repository.DumConnectorIndexingRepository;
import com.viglet.dumont.connector.persistence.repository.DumConnectorIndexingStatsRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

class DumConnectorSummaryApiTest {

    private final RestClient restClient = mock(RestClient.class);
    private final DumTuringCsrfService csrfService = mock(DumTuringCsrfService.class);
    private final DumConnectorIndexingRepository indexingRepository =
            mock(DumConnectorIndexingRepository.class);
    private final DumConnectorIndexingStatsRepository statsRepository =
            mock(DumConnectorIndexingStatsRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);

    private DumConnectorSummaryApi api() {
        return new DumConnectorSummaryApi(restClient, csrfService, indexingRepository,
                statsRepository, entityManager, "turing");
    }

    @Test
    void returnsErrorResponseWhenTuringUnreachableButCollectsFullData() {
        // populated source-table rows and indexing activity
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        org.mockito.Mockito.doReturn(List.<Object[]>of(new Object[] {"sourceA", "http://x", "/content"}))
                .when(query).getResultList();
        when(indexingRepository.countByProviderGroupBySource("turing"))
                .thenReturn(List.<Object[]>of(new Object[] {"sourceA", 5L}));
        when(indexingRepository.countByProvider("turing")).thenReturn(5L);
        DumConnectorIndexingStatsModel stat = DumConnectorIndexingStatsModel.builder()
                .source("sourceA").documentCount(5L).build();
        when(statsRepository.findAllByOrderByStartTimeDesc(org.mockito.ArgumentMatchers.any(Limit.class)))
                .thenReturn(List.of(stat));
        // make the downstream call fail so we hit the catch branch deterministically
        when(csrfService.fetch()).thenThrow(new IllegalStateException("no turing"));

        ResponseEntity<SummaryResponse> response = api().generateSummary(false);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error()).contains("Failed to connect to Turing");
    }

    @Test
    void handlesEmptyDataAndMissingPluginTables() {
        // plugin tables absent -> createNativeQuery throws, swallowed
        when(entityManager.createNativeQuery(anyString()))
                .thenThrow(new IllegalArgumentException("no such table"));
        when(indexingRepository.countByProviderGroupBySource("turing")).thenReturn(List.of());
        when(indexingRepository.countByProvider("turing")).thenReturn(0L);
        lenient().when(statsRepository.findAllByOrderByStartTimeDesc(
                org.mockito.ArgumentMatchers.any(Limit.class))).thenReturn(List.of());
        when(csrfService.fetch()).thenThrow(new IllegalStateException("no turing"));

        ResponseEntity<SummaryResponse> response = api().generateSummary(true);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
    }
}
