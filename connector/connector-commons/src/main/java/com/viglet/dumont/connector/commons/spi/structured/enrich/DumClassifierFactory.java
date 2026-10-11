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
 * Builds the LLM-backed {@link DumClassifier} a plugin hands to a {@link DumClassificationEnricher}
 * (roadmap <strong>D106</strong>). A classifier is specific to its task, so the plugin supplies
 * the instruction and allowed labels as the {@code systemPrompt}, and the factory supplies the
 * model wiring, which a plugin cannot reach on its own.
 *
 * <p>
 * The connector registers one as a Spring bean only when {@code dumont.enrichment.llm.enabled} is
 * {@code true}. A plugin asks for it as an optional dependency (an {@code ObjectProvider} or an
 * {@code Optional}), so without an LLM it runs its deterministic steps alone, and never fabricates
 * a label in the LLM's place.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@FunctionalInterface
public interface DumClassifierFactory {

    /**
     * A classifier answering one label for an input, or nothing.
     *
     * @param systemPrompt   the classification instruction and its allowed labels
     * @param cacheKeyPrefix a prefix that keeps this task's cached answers apart from others'
     */
    DumClassifier create(String systemPrompt, String cacheKeyPrefix);
}
