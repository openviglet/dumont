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

package com.viglet.dumont.connector.commons.spi.structured;

import java.util.Collection;
import java.util.Optional;

/**
 * Resolves the {@link DumStructuredSource}s a {@link DumStructuredSourcePlugin} manages.
 *
 * <p>
 * A single connector plugin usually owns several configured sources (looked up by name in its own
 * store). This provider is the seam between the generic indexing loop and a concrete plugin's
 * persistence: the plugin needs only to answer "which sources exist?" and "give me the source named
 * X" — everything else (discover, extract, ground, publish) is handled generically.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface DumStructuredSourceProvider {

    /** The provider name reported by the owning plugin. */
    String getProviderName();

    /** The names of every source this provider manages. */
    Collection<String> sourceNames();

    /** Resolve a configured source by name, if this provider manages it. */
    Optional<DumStructuredSource> resolve(String sourceName);
}
