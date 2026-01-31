/*
 * Copyright (C) 2016-2025 the original author or authors.
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

package com.viglet.dumont.connector.commons.strategy;

import com.viglet.turing.client.sn.job.TurSNJobItems;

/**
 * Strategy Pattern interface for different indexing providers.
 * Each implementation handles provider-specific indexing logic.
 * 
 * Example implementations:
 * - SolrIndexingStrategy
 * - ElasticsearchIndexingStrategy
 * - TuringIndexingStrategy
 * 
 * @author Viglet Team
 * @since 2026.1.4
 */
public interface IndexingStrategy {
    
    /**
     * Process and index the given job items using provider-specific logic.
     * 
     * @param items the job items to be indexed
     */
    void process(TurSNJobItems items);
    
    /**
     * Check if this strategy supports the given provider.
     * 
     * @param provider the provider name (e.g., "SOLR", "ELASTICSEARCH", "TURING")
     * @return true if this strategy supports the provider
     */
    boolean supports(String provider);
    
    /**
     * Get the provider name that this strategy handles.
     * 
     * @return the provider name
     */
    String getProviderName();
    
    /**
     * Perform a health check on the indexing provider.
     * 
     * @return true if the provider is healthy and accessible
     */
    default boolean healthCheck() {
        return true;
    }
}
