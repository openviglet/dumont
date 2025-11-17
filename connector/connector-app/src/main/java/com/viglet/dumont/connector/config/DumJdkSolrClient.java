package com.viglet.dumont.connector.config;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DumJdkSolrClient {
    private final String solrEndpoint;

    public DumJdkSolrClient(@Value("${dumont.solr.endpoint:http://localhost:8983}") String solrEndpoint) {
        this.solrEndpoint = solrEndpoint;
    }

    @Bean
    public SolrClient solrClient() {
        return new HttpJdkSolrClient.Builder("%s/solr".formatted(solrEndpoint))
                .build();
    }
}
