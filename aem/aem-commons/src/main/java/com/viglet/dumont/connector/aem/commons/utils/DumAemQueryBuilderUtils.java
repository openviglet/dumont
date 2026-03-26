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

import org.json.JSONArray;
import org.json.JSONObject;

import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for querying AEM content via the QueryBuilder API.
 * Provides an alternative to infinity.json for discovering content paths.
 *
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
public class DumAemQueryBuilderUtils {

    private static final String QUERY_BUILDER_PATH = "/bin/querybuilder.json";
    private static final int PAGE_SIZE = 500;

    private DumAemQueryBuilderUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Queries the AEM QueryBuilder API to find all content paths of a given type
     * under the configured root path.
     *
     * @param configuration the AEM source configuration
     * @return list of content paths matching the query
     */
    public static List<String> queryAllPaths(DumAemConfiguration configuration) {
        String rootPath = configuration.getRootPath();
        String contentType = configuration.getContentType();

        log.info("QueryBuilder: querying all '{}' under '{}'", contentType, rootPath);

        List<String> allPaths = new ArrayList<>();
        int offset = 0;
        boolean hasMore = true;

        while (hasMore) {
            Optional<JSONObject> response = executeQuery(configuration, rootPath,
                    contentType, offset, PAGE_SIZE);

            if (response.isEmpty()) {
                log.warn("QueryBuilder: empty response at offset {}", offset);
                break;
            }

            JSONObject json = response.get();
            JSONArray hits = json.optJSONArray("hits");

            if (hits == null || hits.isEmpty()) {
                break;
            }

            for (int i = 0; i < hits.length(); i++) {
                JSONObject hit = hits.getJSONObject(i);
                String path = hit.optString("path");
                if (path != null && !path.isBlank()) {
                    allPaths.add(path);
                }
            }

            boolean moreResults = json.optBoolean("more", false);
            if (moreResults && hits.length() == PAGE_SIZE) {
                offset += PAGE_SIZE;
            } else {
                hasMore = false;
            }
        }

        log.info("QueryBuilder: found {} paths under '{}'", allPaths.size(), rootPath);
        return allPaths;
    }

    /**
     * Executes a single paginated QueryBuilder request.
     */
    private static Optional<JSONObject> executeQuery(DumAemConfiguration configuration,
            String rootPath, String contentType, int offset, int limit) {
        String queryUrl = buildQueryUrl(configuration.getUrl(), rootPath, contentType,
                offset, limit);

        try {
            return DumAemCommonsUtils.getResponseBody(queryUrl, configuration, false)
                    .filter(DumAemCommonsUtils::isResponseBodyJSONObject)
                    .map(JSONObject::new);
        } catch (IOException e) {
            log.error("QueryBuilder: failed to execute query at offset {}: {}",
                    offset, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private static String buildQueryUrl(String aemUrl, String rootPath,
            String contentType, int offset, int limit) {
        return "%s%s?path=%s&type=%s&p.offset=%d&p.limit=%d&p.hits=full&p.nodedepth=-1".formatted(
                aemUrl, QUERY_BUILDER_PATH, rootPath, contentType, offset, limit);
    }
}
