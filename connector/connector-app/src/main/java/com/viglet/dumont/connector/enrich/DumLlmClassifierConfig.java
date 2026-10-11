/*
 *
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

package com.viglet.dumont.connector.enrich;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.commons.spi.structured.enrich.DumClassifierFactory;
import com.viglet.dumont.connector.config.DumTuringCsrfService;

/**
 * Exposes the LLM classifier to plugins (roadmap <strong>D106</strong>): with
 * {@code dumont.enrichment.llm.enabled=true}, a {@link DumClassifierFactory} bean builds
 * {@link DumTuringClassifier}s over the shared Turing {@code RestClient}, so a plugin's
 * {@code DumClassificationEnricher} gets a model it could not reach from its own classpath. With
 * the flag off (the default), there is no bean, and plugins run their deterministic steps alone.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Configuration
public class DumLlmClassifierConfig {

    @Bean
    @ConditionalOnProperty(name = "dumont.enrichment.llm.enabled", havingValue = "true")
    DumClassifierFactory dumClassifierFactory(RestClient restClient, DumTuringCsrfService csrfService) {
        return (systemPrompt, cacheKeyPrefix) ->
                new DumTuringClassifier(restClient, csrfService, systemPrompt, cacheKeyPrefix);
    }
}
