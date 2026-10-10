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

package com.viglet.dumont.connector.plugin.jobs.source.http;

import java.net.URI;
import java.util.Map;

/**
 * Performs one partner API GET and returns the response body (trabalharei <strong>TRA15</strong>).
 * The seam exists so adapters are tested against recorded fixtures — a test never reaches the
 * network. The caller has already asked the call budget; the fetcher does not.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface DumJobFetcher {

    /**
     * GET {@code uri} with {@code headers} and return the body. A non-2xx answer or a transport error
     * throws {@link DumJobFetchException}; the message never carries the query string, where partner
     * credentials travel.
     */
    String get(URI uri, Map<String, String> headers);
}
