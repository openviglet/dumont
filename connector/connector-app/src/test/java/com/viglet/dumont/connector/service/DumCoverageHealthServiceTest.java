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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.api.DumConnectorSummaryApi.SummaryResponse;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageSnapshot;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumFieldCoverage;
import com.viglet.dumont.connector.config.DumTuringCsrfService;

class DumCoverageHealthServiceTest {

    private final RestClient restClient = mock(RestClient.class);
    private final DumTuringCsrfService csrfService = mock(DumTuringCsrfService.class);
    private final DumCoverageHistoryStore historyStore = mock(DumCoverageHistoryStore.class);

    private DumCoverageHealthService service() {
        return new DumCoverageHealthService(restClient, csrfService, historyStore);
    }

    @Test
    void summarizeReportsNoHistory() {
        when(historyStore.history("s")).thenReturn(List.of());

        SummaryResponse response = service().summarize("s", 25, false);

        assertThat(response.success()).isFalse();
        assertThat(response.error()).contains("No coverage history");
    }

    @Test
    void summarizeBuildsDataThenReturnsErrorWhenTuringUnreachable() {
        DumCoverageSnapshot snapshot = new DumCoverageSnapshot("s", 1_700_000_000_000L, 100,
                List.of(new DumFieldCoverage("title", "STRING", true, 90, 100, 90)));
        when(historyStore.history("s")).thenReturn(List.of(snapshot));
        when(csrfService.fetch()).thenThrow(new IllegalStateException("no turing"));

        SummaryResponse response = service().summarize("s", 25, true);

        assertThat(response.success()).isFalse();
        assertThat(response.error()).contains("Failed to connect to Turing");
    }
}
