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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.dumont.connector.commons.plugin.DumIndexingDeliveryException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;

class DumElasticsearchIndexingPluginTest {

    private DumElasticsearchIndexingPlugin plugin;
    private ElasticsearchClient client;

    @BeforeEach
    void setUp() {
        plugin = new DumElasticsearchIndexingPlugin("http://localhost:9200", "dumont", null, null);
        client = mock(ElasticsearchClient.class);
        ReflectionTestUtils.setField(plugin, "client", client);
    }

    private static TurSNJobItems itemsWith(Map<String, Object> attributes) {
        TurSNJobItems items = new TurSNJobItems();
        items.add(new TurSNJobItem(TurSNJobAction.CREATE, List.of("Sample"), Locale.US, attributes));
        return items;
    }

    @Test
    void getProviderNameIsElasticsearch() {
        assertThat(plugin.getProviderName()).isEqualTo("ELASTICSEARCH");
    }

    @Test
    void indexNullOrEmptyIsNoOp() throws Exception {
        plugin.index(null);
        plugin.index(new TurSNJobItems());
        verify(client, never()).bulk(any(BulkRequest.class));
    }

    @Test
    void indexSendsBulkRequestOnSuccess() throws Exception {
        BulkResponse response = mock(BulkResponse.class);
        when(response.errors()).thenReturn(false);
        when(client.bulk(any(BulkRequest.class))).thenReturn(response);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "c1");
        attributes.put("title", "Algorithms");

        plugin.index(itemsWith(attributes));

        verify(client).bulk(any(BulkRequest.class));
    }

    @Test
    void indexLogsErrorsWhenBulkResponseHasErrors() throws Exception {
        BulkResponse response = mock(BulkResponse.class);
        when(response.errors()).thenReturn(true);
        when(response.items()).thenReturn(List.of());
        when(client.bulk(any(BulkRequest.class))).thenReturn(response);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "c1");

        plugin.index(itemsWith(attributes));

        verify(response).items();
    }

    @Test
    void indexWrapsIoExceptionInRuntimeException() throws Exception {
        when(client.bulk(any(BulkRequest.class))).thenThrow(new IOException("network down"));
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "c1");
        var items = itemsWith(attributes);

        assertThatThrownBy(() -> plugin.index(items)).isInstanceOf(DumIndexingDeliveryException.class);
    }

    @Test
    void destroyClosesRestClient() {
        // restClient is a real (unconnected) client; closing it must not throw
        assertThatCode(() -> plugin.destroy()).doesNotThrowAnyException();
    }
}
