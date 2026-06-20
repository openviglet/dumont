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

package com.viglet.dumont.connector.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.commons.exception.TurRuntimeException;

class DumSolrIndexingPluginTest {

    private DumSolrIndexingPlugin plugin;
    private SolrClient solrClient;

    @BeforeEach
    void setUp() {
        plugin = new DumSolrIndexingPlugin("http://localhost:8983/solr", "dumont");
        solrClient = mock(SolrClient.class);
        ReflectionTestUtils.setField(plugin, "solrClient", solrClient);
    }

    private static TurSNJobItems itemsWith(Map<String, Object> attributes) {
        TurSNJobItems items = new TurSNJobItems();
        items.add(new TurSNJobItem(TurSNJobAction.CREATE, List.of("Sample"), Locale.US, attributes));
        return items;
    }

    @Test
    void getProviderNameIsSolr() {
        assertThat(plugin.getProviderName()).isEqualTo("SOLR");
    }

    @Test
    void indexNullOrEmptyIsNoOp() throws Exception {
        plugin.index(null);
        plugin.index(new TurSNJobItems());
        verify(solrClient, never()).add(eq("dumont"), anyList());
    }

    @Test
    void indexAddsDocumentsAndCommits() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "c1");
        attributes.put("title", "Algorithms");

        plugin.index(itemsWith(attributes));

        verify(solrClient).add(eq("dumont"), anyList());
        verify(solrClient).commit("dumont");
    }

    @Test
    void indexSkipsItemsWithoutAttributesButStillCommits() throws Exception {
        TurSNJobItems items = new TurSNJobItems();
        items.add(new TurSNJobItem(TurSNJobAction.CREATE, List.of("Sample"), Locale.US,
                new HashMap<>()));

        plugin.index(items);

        verify(solrClient, never()).add(eq("dumont"), anyList());
        verify(solrClient).commit("dumont");
    }

    @Test
    void indexWrapsSolrErrorsInRuntimeException() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "c1");
        when(solrClient.add(eq("dumont"),
                org.mockito.ArgumentMatchers.<List<SolrInputDocument>>any()))
                .thenThrow(new SolrServerException("boom"));
        var items = itemsWith(attributes);

        assertThatThrownBy(() -> plugin.index(items)).isInstanceOf(TurRuntimeException.class);
    }

    @Test
    void destroyClosesClient() throws Exception {
        plugin.destroy();
        verify(solrClient).close();
    }
}
