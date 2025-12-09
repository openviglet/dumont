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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.plugin.DumIndexingPlugin;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;

import lombok.extern.slf4j.Slf4j;

/**
 * Indexing plugin for Apache Solr
 * 
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "dumont.indexing.provider", havingValue = "solr")
public class DumSolrIndexingPlugin implements DumIndexingPlugin {
    
    private final String solrUrl;
    private final String solrCollection;
    private final SolrClient solrClient;

    public DumSolrIndexingPlugin(
            @Value("${dumont.indexing.solr.url}") String solrUrl,
            @Value("${dumont.indexing.solr.collection}") String solrCollection) {
        this.solrUrl = solrUrl;
        this.solrCollection = solrCollection;
        this.solrClient = new Http2SolrClient.Builder(solrUrl).build();
        log.info("Initialized Solr indexing plugin with URL: {} and collection: {}", solrUrl, solrCollection);
    }

    @Override
    public void index(TurSNJobItems turSNJobItems) {
        if (turSNJobItems == null || turSNJobItems.getTuringDocuments().isEmpty()) {
            log.debug("No items to index to Solr");
            return;
        }
        
        log.debug("Indexing {} items to Solr", turSNJobItems.getTuringDocuments().size());
        
        try {
            List<SolrInputDocument> documents = new ArrayList<>();
            
            for (TurSNJobItem item : turSNJobItems) {
                // Skip COMMIT items (they don't have attributes)
                if (item.getAttributes() != null && !item.getAttributes().isEmpty()) {
                    SolrInputDocument doc = new SolrInputDocument();
                    
                    // Add all attributes from the job item to Solr document
                    for (Map.Entry<String, Object> entry : item.getAttributes().entrySet()) {
                        doc.addField(entry.getKey(), entry.getValue());
                    }
                    
                    documents.add(doc);
                }
            }
            
            // Add documents to Solr
            if (!documents.isEmpty()) {
                solrClient.add(solrCollection, documents);
                log.debug("Added {} documents to Solr", documents.size());
            }
            
            // Commit the changes
            solrClient.commit(solrCollection);
            log.info("Successfully indexed {} items to Solr collection: {}", 
                    documents.size(), solrCollection);
            
        } catch (SolrServerException | IOException e) {
            log.error("Error indexing to Solr: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to index items to Solr", e);
        }
    }

    @Override
    public String getProviderName() {
        return "SOLR";
    }
}
