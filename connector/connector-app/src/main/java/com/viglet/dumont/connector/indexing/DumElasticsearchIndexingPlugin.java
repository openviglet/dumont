/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.indexing;

import java.io.IOException;
import java.util.Map;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.plugin.DumIndexingPlugin;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.commons.exception.TurRuntimeException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Indexing plugin for Elasticsearch
 * 
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "dumont.indexing.provider", havingValue = "elasticsearch")
public class DumElasticsearchIndexingPlugin implements DumIndexingPlugin {

    private final String elasticsearchUrl;
    private final String elasticsearchIndex;
    private final String elasticsearchUsername;
    private final String elasticsearchPassword;
    private final ElasticsearchClient client;
    private final RestClient restClient;

    public DumElasticsearchIndexingPlugin(
            @Value("${dumont.indexing.elasticsearch.url}") String elasticsearchUrl,
            @Value("${dumont.indexing.elasticsearch.index}") String elasticsearchIndex,
            @Value("${dumont.indexing.elasticsearch.username:#{null}}") String elasticsearchUsername,
            @Value("${dumont.indexing.elasticsearch.password:#{null}}") String elasticsearchPassword) {
        this.elasticsearchUrl = elasticsearchUrl;
        this.elasticsearchIndex = elasticsearchIndex;
        this.elasticsearchUsername = elasticsearchUsername;
        this.elasticsearchPassword = elasticsearchPassword;

        // Create the client and store both client and restClient for proper cleanup
        HttpHost httpHost = HttpHost.create(elasticsearchUrl);

        try {
            if (elasticsearchUsername != null && !elasticsearchUsername.isEmpty()) {
                BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(
                        AuthScope.ANY,
                        new UsernamePasswordCredentials(elasticsearchUsername, elasticsearchPassword));

                this.restClient = RestClient.builder(httpHost)
                        .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                                .setDefaultCredentialsProvider(credentialsProvider))
                        .build();
            } else {
                this.restClient = RestClient.builder(httpHost).build();
            }

            RestClientTransport transport = new RestClientTransport(
                    this.restClient, new JacksonJsonpMapper());

            this.client = new ElasticsearchClient(transport);
            log.info("Initialized Elasticsearch indexing plugin with URL: {} and index: {}",
                    elasticsearchUrl, elasticsearchIndex);
        } catch (Exception e) {
            log.error("Error creating Elasticsearch client: {}", e.getMessage(), e);
            throw new TurRuntimeException("Failed to create Elasticsearch client", e);
        }
    }

    @Override
    public void index(TurSNJobItems turSNJobItems) {
        if (turSNJobItems == null || turSNJobItems.getTuringDocuments().isEmpty()) {
            log.debug("No items to index to Elasticsearch");
            return;
        }

        log.debug("Indexing {} items to Elasticsearch", turSNJobItems.getTuringDocuments().size());

        try {
            BulkRequest bulkRequest = buildBulkRequest(turSNJobItems);
            int operationCount = turSNJobItems.getTuringDocuments().stream()
                    .filter(item -> item.getAttributes() != null && !item.getAttributes().isEmpty())
                    .toArray().length;

            if (operationCount > 0) {
                BulkResponse result = client.bulk(bulkRequest);

                handleBulkResponse(result, operationCount);
            }

        } catch (IOException e) {
            log.error("Error indexing to Elasticsearch: {}", e.getMessage(), e);
            throw new TurRuntimeException("Failed to index items to Elasticsearch", e);
        }
    }

    private BulkRequest buildBulkRequest(TurSNJobItems turSNJobItems) {
        BulkRequest.Builder bulkRequest = new BulkRequest.Builder();

        for (TurSNJobItem item : turSNJobItems) {
            if (item.getAttributes() != null && !item.getAttributes().isEmpty()) {
                String id = item.getId();
                Map<String, Object> attributes = item.getAttributes();

                bulkRequest.operations(op -> op
                        .index(idx -> idx
                                .index(elasticsearchIndex)
                                .id(id)
                                .document(attributes)));
            }
        }
        return bulkRequest.build();
    }

    private void handleBulkResponse(BulkResponse result, int operationCount) {
        if (result.errors()) {
            log.error("Bulk indexing had errors");
            for (BulkResponseItem item : result.items()) {
                if (item.error() != null) {
                    log.error("Error indexing document {}: {}",
                            item.id(), item.error().reason());
                }
            }
        } else {
            log.info("Successfully indexed {} items to Elasticsearch index: {}",
                    operationCount, elasticsearchIndex);
        }
    }

    @Override
    public String getProviderName() {
        return "ELASTICSEARCH";
    }

    @PreDestroy
    public void destroy() {
        try {
            if (restClient != null) {
                restClient.close();
                log.info("Elasticsearch client closed successfully");
            }
        } catch (IOException e) {
            log.error("Error closing Elasticsearch client: {}", e.getMessage(), e);
        }
    }
}
