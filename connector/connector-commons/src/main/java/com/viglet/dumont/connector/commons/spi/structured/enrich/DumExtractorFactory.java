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

package com.viglet.dumont.connector.commons.spi.structured.enrich;

/**
 * Builds the LLM-backed {@link DumExtractor} a plugin hands to a {@link DumExtractionEnricher}
 * (roadmap <strong>D131</strong>), as {@link DumClassifierFactory} does for a classifier: the
 * plugin supplies the task prompt, the factory the model wiring.
 *
 * <p>
 * Registered under the same flag as the classifier factory, {@code dumont.enrichment.llm.enabled},
 * and asked for as an optional dependency, so without an LLM a plugin publishes no extracted field
 * rather than a guessed one.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@FunctionalInterface
public interface DumExtractorFactory {

    /**
     * An extractor listing the values {@code systemPrompt} asks for.
     *
     * @param systemPrompt   what to list, e.g. "the skills this job requires"
     * @param cacheKeyPrefix a prefix that keeps this task's cached answers apart from others'
     */
    DumExtractor create(String systemPrompt, String cacheKeyPrefix);
}
