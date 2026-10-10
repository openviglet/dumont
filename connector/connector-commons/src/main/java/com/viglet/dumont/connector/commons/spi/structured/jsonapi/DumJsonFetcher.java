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

package com.viglet.dumont.connector.commons.spi.structured.jsonapi;

import java.io.IOException;

/**
 * The seam a {@link DumJsonApiSource} fetches through (roadmap <strong>D92</strong>): one resolved
 * request in, the response body out. {@link DumHttpJsonFetcher} is the production one; a test
 * passes a lambda.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@FunctionalInterface
public interface DumJsonFetcher {

    /**
     * Sends the request and returns the response body.
     *
     * @throws IOException on a transport failure or a non-2xx status
     */
    String fetch(DumJsonApiRequest request) throws IOException;
}
