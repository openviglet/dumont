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

package com.viglet.dumont.connector.plugin.jobs.source;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobFetcher;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobRequest;

/** The job site's tombstone endpoint as a fetcher: records every POST, and fails while told to. */
public final class FakeSite implements DumJobFetcher {

    public final List<DumJobRequest> requests = new ArrayList<>();
    public RuntimeException failure;

    @Override
    public String get(URI uri, Map<String, String> headers) {
        throw new UnsupportedOperationException("The site is only POSTed to");
    }

    @Override
    public String send(DumJobRequest request) {
        requests.add(request);
        if (failure != null) {
            throw failure;
        }
        return "";
    }

    /** The bodies POSTed so far. */
    public List<String> bodies() {
        return requests.stream().map(DumJobRequest::body).toList();
    }
}
