/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.eds.commons.ext;

import java.util.Optional;

import com.viglet.dumont.connector.eds.commons.DumEDSContext;
import com.viglet.turing.client.sn.TurMultiValue;

/**
 * Generic attribute-extension contract. Plugin authors implement this when the
 * built-in extraction pipeline (JSON-sheet column → og:* → facet derivation →
 * fieldMapping) doesn't fit the site's quirks.
 *
 * <p>Return {@link Optional#empty()} to signal "no value" without raising —
 * the plugin process will simply skip the attribute.
 *
 * <p>Implementations must be safe to invoke from multiple threads concurrently:
 * the plugin process fans out per-page fetches via virtual threads, and the
 * resolved instance is cached per className in
 * {@link com.viglet.dumont.commons.cache.DumCustomClassCache}.
 */
public interface DumEDSExtInterface {
    Optional<TurMultiValue> consume(DumEDSContext context);
}
