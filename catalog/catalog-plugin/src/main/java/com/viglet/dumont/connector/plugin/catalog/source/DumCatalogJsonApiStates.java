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

package com.viglet.dumont.connector.plugin.catalog.source;

import com.viglet.dumont.connector.commons.spi.structured.jsonapi.DumJsonApiState;

/**
 * Hands each JSON API catalog source the state it keeps between runs (roadmap
 * <strong>D92</strong>): budget windows and last-seen ids, keyed by source name.
 * {@link DumJpaCatalogJsonApiStates} is the production one; a test passes a lambda over
 * {@code DumInMemoryJsonApiState}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@FunctionalInterface
public interface DumCatalogJsonApiStates {

    /** The state of the source named {@code sourceName}. */
    DumJsonApiState forSource(String sourceName);
}
