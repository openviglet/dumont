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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogSource;

import lombok.extern.slf4j.Slf4j;

/**
 * Default {@link DumCatalogFetcher}: an HTTP GET of the source's catalog URL via the JDK
 * {@link HttpClient}, with an optional request header for authenticated sidecar endpoints. A
 * non-2xx response or transport error throws {@link DumCatalogFetchException} so the run reports the
 * failure rather than silently indexing nothing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class DumCatalogHttpFetcher implements DumCatalogFetcher {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String fetch(DumCatalogSource source) {
        String url = source.getCatalogUrl();
        if (url == null || url.isBlank()) {
            throw new DumCatalogFetchException(
                    "Catalog source '" + source.getName() + "' has no catalogUrl");
        }
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Accept", "application/json")
                .GET();
        if (source.getAuthHeaderName() != null && !source.getAuthHeaderName().isBlank()
                && source.getAuthHeaderValue() != null) {
            request.header(source.getAuthHeaderName(), source.getAuthHeaderValue());
        }
        try {
            HttpResponse<String> response =
                    httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new DumCatalogFetchException("Catalog fetch for '" + source.getName()
                        + "' returned HTTP " + response.statusCode() + " from " + url);
            }
            log.info("Fetched catalog for source '{}' ({} bytes) from {}", source.getName(),
                    response.body().length(), url);
            return response.body();
        } catch (IOException e) {
            throw new DumCatalogFetchException(
                    "Catalog fetch for '" + source.getName() + "' failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DumCatalogFetchException(
                    "Catalog fetch for '" + source.getName() + "' was interrupted", e);
        }
    }
}
