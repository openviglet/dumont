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

package com.viglet.dumont.connector.plugin.catalog.migration;

/**
 * Which indexing leg a catalog source currently runs on (roadmap <strong>D11</strong>).
 *
 * <p>
 * A source starts on {@link #SIDECAR} — the catalog bridge fetching already-normalized JSON from the
 * external (Python) engine (D10). It flips to {@link #NATIVE} — a native declarative strategy
 * materialized from a D19 recipe — only once a {@code DumParityGate} run passes for that source. The
 * flip is per-source, so the hardest anti-bot sources may stay on the sidecar indefinitely.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum DumCatalogMigrationState {

    /** The catalog bridge re-indexes the external engine's normalized JSON (D10, the default). */
    SIDECAR,

    /** A native declarative recipe crawls the public source directly, the Python leg retired. */
    NATIVE
}
