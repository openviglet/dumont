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

package com.viglet.dumont.connector.plugin.catalog.fetch;

import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogSource;

/**
 * Fetches the raw normalized-catalog JSON for a source. The seam exists so the HTTP transport can be
 * stubbed in tests — the bridge logic (parse, ground, publish) is exercised without a live endpoint.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface DumCatalogFetcher {

    /** Fetch the catalog payload for {@code source}, as a JSON string. */
    String fetch(DumCatalogSource source);
}
