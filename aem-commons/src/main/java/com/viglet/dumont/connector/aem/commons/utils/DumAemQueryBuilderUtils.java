/*
 * Copyright (C) 2016-2025 the original author or authors.
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

package com.viglet.dumont.connector.aem.commons.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;

import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for querying AEM content via the QueryBuilder API.
 * Provides lightweight discovery of all content paths using slim hits.
 *
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
public class DumAemQueryBuilderUtils {

    private static final String QUERY_BUILDER_PATH = "/bin/querybuilder.json";
    private static final int SLIM_PAGE_SIZE = 500;

    private DumAemQueryBuilderUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Discovers all content paths and collects them into a list.
     * Suitable for auditing and small/medium repositories.
     *
     * @param configuration the AEM source configuration
     * @return list of content paths
     */
    public static List<String> queryAllPaths(DumAemConfiguration configuration) {
        List<String> allPaths = new ArrayList<>();
        discoverPaths(configuration, allPaths::addAll);
        return allPaths;
    }

    /**
     * Discovers content paths in pages and streams each page to the consumer
     * as it arrives. Never holds the full path list in memory.
     * <p>
     * The first request fetches the total count. All subsequent page offsets
     * are then fetched in parallel and each batch is delivered to the consumer
     * immediately.
     *
     * @param configuration the AEM source configuration
     * @param pageConsumer  receives a batch of paths for each page
     * @return the total number of paths discovered
     */
    public static long discoverPaths(DumAemConfiguration configuration,
            Consumer<List<String>> pageConsumer) {
        String rootPath = configuration.getRootPath();
        String contentType = configuration.getContentType();

        log.info("QueryBuilder: discovering all '{}' under '{}'",
                contentType, rootPath);

        // First request to get total
        JSONObject firstJson = executeSlimQuery(configuration, rootPath, contentType, 0)
                .orElse(null);

        if (firstJson == null) {
            log.warn("QueryBuilder: no response for first page");
            return 0;
        }

        long total = firstJson.optLong("total", 0);
        log.info("QueryBuilder: total reported by AEM: {}", total);

        if (total == 0) {
            return 0;
        }

        // Process first page
        JSONArray firstHits = firstJson.optJSONArray("hits");
        long collected = deliverPage(firstHits, pageConsumer);

        log.info("QueryBuilder: page offset=0, collected={}/{}", collected, total);

        // Remaining pages
        for (int offset = SLIM_PAGE_SIZE; offset < total; offset += SLIM_PAGE_SIZE) {
            JSONObject json = executeSlimQuery(configuration, rootPath, contentType, offset)
                    .orElse(null);

            if (json == null) {
                break;
            }

            JSONArray hits = json.optJSONArray("hits");
            long pageCount = deliverPage(hits, pageConsumer);
            collected += pageCount;

            log.info("QueryBuilder: page offset={}, collected={}/{}",
                    offset, collected, total);

            if (pageCount == 0) {
                break;
            }
        }

        log.info("QueryBuilder: discovered {} total paths under '{}'",
                collected, rootPath);
        return collected;
    }

    private static long deliverPage(JSONArray hits, Consumer<List<String>> pageConsumer) {
        if (hits == null || hits.isEmpty()) {
            return 0;
        }
        List<String> paths = new ArrayList<>(hits.length());
        extractPaths(hits, paths);
        if (!paths.isEmpty()) {
            pageConsumer.accept(paths);
        }
        return paths.size();
    }

    private static void extractPaths(JSONArray hits, List<String> allPaths) {
        for (int i = 0; i < hits.length(); i++) {
            String path = extractPath(hits.get(i));
            if (path != null && !path.isBlank()) {
                allPaths.add(path);
            }
        }
    }

    private static String extractPath(Object hit) {
        if (hit instanceof String hitStr) {
            return hitStr;
        }
        if (hit instanceof JSONObject hitObj) {
            if (hitObj.has("jcr:path")) {
                return hitObj.optString("jcr:path");
            }
            if (hitObj.has("path")) {
                return hitObj.optString("path");
            }
        }
        return null;
    }

    private static Optional<JSONObject> executeSlimQuery(DumAemConfiguration configuration,
            String rootPath, String contentType, int offset) {
        return executeQuery(configuration,
                "path=%s&type=%s&p.offset=%d&p.limit=%d&p.hits=slim"
                        .formatted(rootPath, contentType, offset, SLIM_PAGE_SIZE));
    }

    private static Optional<JSONObject> executeQuery(DumAemConfiguration configuration,
            String queryParams) {
        String queryUrl = "%s%s?%s".formatted(
                configuration.getUrl(), QUERY_BUILDER_PATH, queryParams);

        try {
            return DumAemCommonsUtils.getResponseBody(queryUrl, configuration, false)
                    .filter(DumAemCommonsUtils::isResponseBodyJSONObject)
                    .map(JSONObject::new);
        } catch (IOException e) {
            log.error("QueryBuilder: query failed: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
}
