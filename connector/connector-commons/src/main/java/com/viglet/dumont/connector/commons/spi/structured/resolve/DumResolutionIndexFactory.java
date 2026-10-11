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

package com.viglet.dumont.connector.commons.spi.structured.resolve;

import java.util.Optional;
import java.util.function.Function;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;

/**
 * Builds a provider's persistent {@link DumResolutionIndex} (roadmap <strong>D109</strong>).
 * {@code connector-app} registers one as a bean; a plugin asks for it as an optional dependency and
 * passes the index it builds to the {@code DumStructuredSourcePlugin} constructor, so resolution
 * survives a restart. Without the bean the plugin keeps a {@link DumInMemoryResolutionIndex}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
@FunctionalInterface
public interface DumResolutionIndexFactory {

    /**
     * The index of {@code provider}'s sources.
     *
     * @param provider       the provider name, which keeps two providers' members apart
     * @param schemaOfSource the schema a source's stored members are read back with; empty for a
     *                       source the provider no longer has, whose members are then skipped
     */
    DumResolutionIndex forProvider(String provider,
            Function<String, Optional<DumStructuredSchema>> schemaOfSource);
}
