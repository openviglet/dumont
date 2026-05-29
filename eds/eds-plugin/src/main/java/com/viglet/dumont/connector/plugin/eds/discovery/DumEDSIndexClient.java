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

package com.viglet.dumont.connector.plugin.eds.discovery;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.viglet.dumont.connector.eds.commons.domain.DumEDSSheetPage;

import lombok.extern.slf4j.Slf4j;

/**
 * Fetches one or more pages of an EDS query-index JSON resource. Pagination uses
 * the EDS-native {@code ?offset=N&limit=N} query parameters; the loop terminates
 * when the server reports it has no more rows.
 *
 * <p>Defense in depth: a hard cap of {@link #MAX_PAGES} iterations prevents an
 * accidental infinite loop should an EDS endpoint return inconsistent {@code
 * total}/{@code offset}/{@code limit} bookkeeping.
 */
@Slf4j
@Component
public class DumEDSIndexClient {

    public static final String DEFAULT_USER_AGENT = "Viglet-Dumont-EDS/1.0";
    public static final int DEFAULT_PAGE_SIZE = 500;
    public static final int MAX_PAGES = 1000;

    private final DumEDSSheetParser parser;
    private final RestClient.Builder restClientBuilder;

    public DumEDSIndexClient(DumEDSSheetParser parser, RestClient.Builder restClientBuilder) {
        this.parser = parser;
        this.restClientBuilder = restClientBuilder;
    }

    /** Fetches a single page (offset/limit not supplied — server defaults apply). */
    public DumEDSSheetPage fetchPage(String baseUrl, String indexPath, String userAgent) {
        return fetchPage(baseUrl, indexPath, userAgent, null, null);
    }

    /** Fetches a single page with explicit offset/limit. */
    public DumEDSSheetPage fetchPage(String baseUrl, String indexPath, String userAgent,
            Integer offset, Integer limit) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        if (indexPath == null || indexPath.isBlank()) {
            throw new IllegalArgumentException("indexPath is required");
        }
        URI uri = buildUri(baseUrl, indexPath, offset, limit);
        log.debug("GET {}", uri);
        RestClient client = restClientBuilder
                .defaultHeader("User-Agent", userAgent == null ? DEFAULT_USER_AGENT : userAgent)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
        String body = client.get().uri(uri).retrieve().body(String.class);
        return parser.parse(body);
    }

    /**
     * Paginates an index endpoint and returns every row across all pages. Order is
     * preserved; duplicates within the same index are kept (caller deduplicates if
     * desired). Uses {@link #DEFAULT_PAGE_SIZE} per request.
     */
    public List<Map<String, String>> fetchAllRows(String baseUrl, String indexPath,
            String userAgent) {
        return fetchAllRows(baseUrl, indexPath, userAgent, DEFAULT_PAGE_SIZE);
    }

    public List<Map<String, String>> fetchAllRows(String baseUrl, String indexPath,
            String userAgent, int pageSize) {
        List<Map<String, String>> aggregated = new ArrayList<>();
        int offset = 0;
        int safetyBudget = MAX_PAGES;
        while (safetyBudget-- > 0) {
            DumEDSSheetPage page = fetchPage(baseUrl, indexPath, userAgent, offset, pageSize);
            log.debug("Fetched page offset={} limit={} rows={} total={}", page.offset(),
                    page.limit(), page.rows().size(), page.total());
            aggregated.addAll(page.rows());
            if (page.rows().isEmpty() || !page.hasMore()) {
                return aggregated;
            }
            int next = page.nextOffset();
            if (next <= offset) {
                log.warn("Pagination not progressing (offset stayed at {}); breaking", offset);
                return aggregated;
            }
            offset = next;
        }
        log.warn("Reached MAX_PAGES={} while paginating {}{} — returning what we have",
                MAX_PAGES, baseUrl, indexPath);
        return aggregated;
    }

    /**
     * Convenience: deduplicates by {@code path} across one or more indexes (later
     * occurrences win when metadata maps differ; this is OK for v1 because
     * subsequent fields are filled by HTML fetch anyway).
     */
    public Map<String, Map<String, String>> fetchAllByPath(String baseUrl,
            List<String> indexPaths, String userAgent) {
        Map<String, Map<String, String>> byPath = new LinkedHashMap<>();
        for (String indexPath : indexPaths) {
            for (Map<String, String> row : fetchAllRows(baseUrl, indexPath, userAgent)) {
                String path = row.get("path");
                if (path == null || path.isBlank()) {
                    continue;
                }
                byPath.put(path, row);
            }
        }
        return byPath;
    }

    static URI buildUri(String baseUrl, String indexPath, Integer offset, Integer limit) {
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String p = indexPath.startsWith("/") ? indexPath : "/" + indexPath;
        UriComponentsBuilder ucb = UriComponentsBuilder.fromUriString(b + p);
        if (offset != null) {
            ucb.queryParam("offset", offset);
        }
        if (limit != null) {
            ucb.queryParam("limit", limit);
        }
        return ucb.build(true).toUri();
    }
}
