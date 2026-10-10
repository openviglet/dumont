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

import java.util.Map;

/**
 * One call a {@link DumJsonApiSource} makes, with every template already resolved (roadmap
 * <strong>D92</strong>): what a {@link DumJsonFetcher} sends, and nothing it has to interpret.
 *
 * @param method  {@code GET} or {@code POST}
 * @param url     the absolute URL, placeholders resolved and encoded
 * @param headers the request headers, secrets resolved
 * @param body    the JSON body of a {@code POST}, or {@code null}
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record DumJsonApiRequest(String method, String url, Map<String, String> headers, String body) {

    public DumJsonApiRequest {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
