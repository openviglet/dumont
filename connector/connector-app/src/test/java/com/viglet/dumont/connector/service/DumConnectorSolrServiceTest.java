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

import org.apache.solr.client.solrj.SolrClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.domain.DumConnectorValidateDifference;

class DumConnectorSolrServiceTest {

    private DumConnectorIndexingService indexingService;
    private DumConnectorSolrService service;

    @BeforeEach
    void setUp() {
        indexingService = mock(DumConnectorIndexingService.class);
        // unstubbed RestClient mock: get() returns null, so the fluent chain NPEs and the
        // service's catch blocks fall back to empty results — the "Turing unreachable" path.
        service = new DumConnectorSolrService(indexingService, mock(SolrClient.class),
                mock(RestClient.class));
    }

    @Test
    void validateContentFallsBackToDumontSitesWhenIndexingHasNone() {
        when(indexingService.getSites("wknd", "turing")).thenReturn(List.of());

        DumConnectorValidateDifference diff = service.validateContent("wknd", "turing");

        assertThat(diff.getMissing()).isEmpty();
        assertThat(diff.getExtra()).isEmpty();
    }

    @Test
    void validateContentWithSiteButNoLocalesProducesNoDifferences() {
        when(indexingService.getSites("wknd", "turing")).thenReturn(List.of("WKND"));
        when(indexingService.getEnvironment("WKND", "turing")).thenReturn(List.of());

        DumConnectorValidateDifference diff = service.validateContent("wknd", "turing");

        assertThat(diff.getMissing()).isEmpty();
        assertThat(diff.getExtra()).isEmpty();
    }

    @Test
    void hasContentIdAtSolrIsFalseWhenNoLocalesResolved() {
        when(indexingService.getSites("wknd", "turing")).thenReturn(List.of("WKND"));

        assertThat(service.hasContentIdAtSolr("id-1", "wknd", "turing")).isFalse();
    }
}
