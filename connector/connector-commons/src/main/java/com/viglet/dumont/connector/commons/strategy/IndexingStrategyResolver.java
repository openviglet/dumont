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

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the appropriate indexing strategy based on the provider name.
 * This class implements the Strategy Pattern by dynamically selecting
 * the correct indexing implementation at runtime.
 * 
 * @author Viglet Team
 * @since 2026.1.4
 */
@Slf4j
@Component
public class IndexingStrategyResolver {
    
    private final List<IndexingStrategy> strategies;
    
    public IndexingStrategyResolver(List<IndexingStrategy> strategies) {
        this.strategies = strategies;
        log.info("Initialized IndexingStrategyResolver with {} strategies", strategies.size());
        strategies.forEach(s -> log.debug("Registered strategy: {}", s.getProviderName()));
    }
    
    /**
     * Resolve and return the appropriate indexing strategy for the given provider.
     * 
     * @param provider the provider name (e.g., "SOLR", "ELASTICSEARCH", "TURING")
     * @return the matching IndexingStrategy
     * @throws UnsupportedProviderException if no strategy supports the provider
     */
    public IndexingStrategy resolve(String provider) {
        log.debug("Resolving indexing strategy for provider: {}", provider);
        
        return strategies.stream()
            .filter(strategy -> strategy.supports(provider))
            .findFirst()
            .orElseThrow(() -> {
                log.error("No indexing strategy found for provider: {}", provider);
                return new UnsupportedProviderException(
                    String.format("Unsupported indexing provider: %s", provider));
            });
    }
    
    /**
     * Check if a provider is supported.
     * 
     * @param provider the provider name
     * @return true if a strategy exists for this provider
     */
    public boolean isSupported(String provider) {
        return strategies.stream()
            .anyMatch(strategy -> strategy.supports(provider));
    }
    
    /**
     * Get all registered provider names.
     * 
     * @return list of supported provider names
     */
    public List<String> getSupportedProviders() {
        return strategies.stream()
            .map(IndexingStrategy::getProviderName)
            .toList();
    }
    
    /**
     * Exception thrown when an unsupported provider is requested.
     */
    public static class UnsupportedProviderException extends RuntimeException {
        public UnsupportedProviderException(String message) {
            super(message);
        }
    }
}
