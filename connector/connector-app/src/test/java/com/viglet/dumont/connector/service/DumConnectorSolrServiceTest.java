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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CursorMarkParams;
import org.apache.solr.common.params.SolrParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.domain.DumConnectorValidateDifference;
import com.viglet.dumont.connector.domain.DumSNSiteLocale;

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

    @Test
    void validateContentComputesMissingAndExtraAgainstSolr() throws Exception {
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        SolrClient solrClient = mock(SolrClient.class);
        DumConnectorSolrService svc =
                new DumConnectorSolrService(indexingService, solrClient, restClient);

        when(indexingService.getSites("wknd", "turing")).thenReturn(List.of("WKND"));
        when(indexingService.getEnvironment("WKND", "turing")).thenReturn(List.of("prod"));
        DumSNSiteLocale locale = new DumSNSiteLocale();
        locale.setCore("wknd_en");
        locale.setLanguage(Locale.ENGLISH);
        when(restClient.get().uri(anyString()).retrieve().body(DumSNSiteLocale[].class))
                .thenReturn(new DumSNSiteLocale[] {locale});
        // DB has /a and /b for this core/environment.
        when(indexingService.listIndexedObjectIds(eq("wknd"), eq("prod"), any(), eq("turing")))
                .thenReturn(List.of("/a", "/b"));
        // Solr returns /b and /c.
        QueryResponse response = mock(QueryResponse.class);
        SolrDocumentList docs = new SolrDocumentList();
        docs.add(solrDoc("/b"));
        docs.add(solrDoc("/c"));
        when(response.getResults()).thenReturn(docs);
        when(response.getNextCursorMark()).thenReturn(CursorMarkParams.CURSOR_MARK_START);
        when(solrClient.query(eq("wknd_en"), any(SolrParams.class))).thenReturn(response);

        DumConnectorValidateDifference diff = svc.validateContent("wknd", "turing");

        assertThat(diff.getExtra().get("wknd_en")).containsExactly("/c");
        assertThat(diff.getMissing().get("wknd_en")).containsExactly("/a");
    }

    @Test
    void hasContentIdAtSolrTrueWhenDocumentFound() throws Exception {
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        SolrClient solrClient = mock(SolrClient.class);
        DumConnectorSolrService svc =
                new DumConnectorSolrService(indexingService, solrClient, restClient);

        when(indexingService.getSites("wknd", "turing")).thenReturn(List.of("WKND"));
        DumSNSiteLocale locale = new DumSNSiteLocale();
        locale.setCore("wknd_en");
        locale.setLanguage(Locale.ENGLISH);
        when(restClient.get().uri(anyString()).retrieve().body(DumSNSiteLocale[].class))
                .thenReturn(new DumSNSiteLocale[] {locale});
        when(solrClient.getById("wknd_en", "id-1")).thenReturn(solrDoc("id-1"));

        assertThat(svc.hasContentIdAtSolr("id-1", "wknd", "turing")).isTrue();
    }

    private static SolrDocument solrDoc(String id) {
        SolrDocument doc = new SolrDocument();
        doc.setField("id", id);
        return doc;
    }
}
