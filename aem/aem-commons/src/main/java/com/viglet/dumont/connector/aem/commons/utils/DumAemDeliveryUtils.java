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

package com.viglet.dumont.connector.aem.commons.utils;

import static com.viglet.dumont.connector.aem.commons.DumAemConstants.ACTIVATE;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_LAST_MODIFIED;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_LAST_REPLICATED;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_LAST_REPLICATED_PUBLISH;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_LAST_REPLICATION_ACTION;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_LAST_REPLICATION_ACTION_PUBLISH;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_PAGE;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_TEMPLATE;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.DATE_JSON_FORMAT;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.JCR_CONTENT;
import static org.apache.jackrabbit.JcrConstants.JCR_PRIMARYTYPE;
import static org.apache.jackrabbit.JcrConstants.JCR_TITLE;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;

import lombok.extern.slf4j.Slf4j;

/**
 * The publish-tier fetch strategy (roadmap <strong>D61</strong>): reaches AEM content through
 * {@code .model.json} on the publish / delivery host instead of the author-tier Sling servlets
 * ({@code .infinity.json}, {@code /bin/querybuilder.json}) the {@code SLING} strategy uses.
 *
 * <p>
 * {@code .model.json} is served by Core Components on publish, is CDN-fronted, and carries both the
 * page's own metadata and — for a hierarchy root — every descendant page under {@code :children}. That
 * is enough for the two things the author servlets were doing:
 * <ul>
 * <li><strong>Discovery / traversal</strong> — {@link #discoverPaths} walks {@code :children} instead
 * of paging {@code /bin/querybuilder.json} or descending {@code .infinity.json} children.</li>
 * <li><strong>Metadata</strong> — {@link #toContentNode} translates the page model into the
 * {@code .infinity.json} shape {@code DumAemObjectGeneric} already parses, so nothing downstream of the
 * fetch changes. Field extraction was already reading {@code .model.json} from the publish prefix
 * (see {@code DumAemExtModelJsonBase}).</li>
 * </ul>
 *
 * <p>
 * Content the delivery tier serves is by definition published, so the translated node is stamped
 * delivered — the same conclusion {@code DumAemEvent.PUBLISHING} forces on the Sling path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public class DumAemDeliveryUtils {

    private static final String MODEL_JSON = ".model.json";
    private static final String CHILDREN = ":children";
    private static final String TITLE = "title";
    private static final String TEMPLATE_NAME = "templateName";
    private static final String LAST_MODIFIED_DATE = "lastModifiedDate";
    /** Depth guard for the {@code :children} descent; a hierarchy root usually needs one level. */
    private static final int MAX_DEPTH = 20;
    /** {@code DATE_JSON_FORMAT} as a {@code java.time} formatter, for the translated dates. */
    private static final DateTimeFormatter MODEL_DATE_FORMATTER = DateTimeFormatter
            .ofPattern(DATE_JSON_FORMAT, Locale.ENGLISH);

    private DumAemDeliveryUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * The host every delivery request is sent to: the dedicated {@code delivery-pXXXX-eYYYY} prefix
     * when configured, otherwise the publish prefix, otherwise the source URL (which lets a
     * single-host on-prem install opt into the strategy without a second prefix).
     */
    public static String deliveryUrlPrefix(DumAemConfiguration configuration) {
        if (StringUtils.isNotBlank(configuration.getDeliveryURLPrefix())) {
            return configuration.getDeliveryURLPrefix();
        }
        if (StringUtils.isNotBlank(configuration.getPublishURLPrefix())) {
            return configuration.getPublishURLPrefix();
        }
        return configuration.getUrl();
    }

    /**
     * Fetches the raw {@code .model.json} for a path from the delivery host, with a
     * {@code ?t=<timestamp>} cache-buster so a CDN hit never hides a republish.
     */
    public static Optional<JSONObject> getModelJson(String path,
            DumAemConfiguration configuration) {
        String url = "%s%s%s?t=%d".formatted(deliveryUrlPrefix(configuration), path, MODEL_JSON,
                System.currentTimeMillis());
        try {
            return DumAemCommonsUtils.getResponseBody(url, configuration, false)
                    .filter(DumAemCommonsUtils::isResponseBodyJSONObject)
                    .map(JSONObject::new);
        } catch (IOException e) {
            log.error("Delivery: model.json request failed for {}: {}", url, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * The delivery-tier stand-in for {@code .infinity.json}: fetches the page model and returns it
     * translated into the JCR node shape the rest of the connector parses.
     */
    public static Optional<JSONObject> getContentNode(String path,
            DumAemConfiguration configuration) {
        return getModelJson(path, configuration).map(modelJson -> toContentNode(path, modelJson));
    }

    /**
     * Translates a Core Components page model into the {@code .infinity.json} node shape.
     *
     * <p>
     * Only what {@code DumAemObjectGeneric} reads is populated: the primary type, the title and
     * template under {@code jcr:content}, the last-modified date in AEM's JSON date format, and the
     * replication properties that mark the page delivered. Direct child pages derived from
     * {@code :children} are added as marker nodes under their <em>relative</em> name, which is what
     * lets the existing traversal keep walking — it re-fetches each child by path and lands back
     * here.
     */
    public static JSONObject toContentNode(String path, JSONObject modelJson) {
        JSONObject jcrContent = new JSONObject();
        putIfPresent(jcrContent, JCR_TITLE, modelJson.optString(TITLE, null));
        putIfPresent(jcrContent, CQ_TEMPLATE, modelJson.optString(TEMPLATE_NAME, null));
        String lastModified = formatModelDate(modelJson.opt(LAST_MODIFIED_DATE));
        putIfPresent(jcrContent, CQ_LAST_MODIFIED, lastModified);
        putIfPresent(jcrContent, CQ_LAST_REPLICATED, lastModified);
        putIfPresent(jcrContent, CQ_LAST_REPLICATED_PUBLISH, lastModified);
        jcrContent.put(CQ_LAST_REPLICATION_ACTION, ACTIVATE);
        jcrContent.put(CQ_LAST_REPLICATION_ACTION_PUBLISH, ACTIVATE);

        JSONObject node = new JSONObject();
        node.put(JCR_PRIMARYTYPE, CQ_PAGE);
        node.put(JCR_CONTENT, jcrContent);
        for (String childName : childNames(path, modelJson)) {
            node.put(childName, new JSONObject().put(JCR_PRIMARYTYPE, CQ_PAGE));
        }
        return node;
    }

    /**
     * Discovers every page under the configured root and collects them into a list — the delivery
     * counterpart of {@link DumAemQueryBuilderUtils#queryAllPaths}.
     */
    public static List<String> queryAllPaths(DumAemConfiguration configuration) {
        List<String> allPaths = new ArrayList<>();
        discoverPaths(configuration, configuration.getRootPath(), allPaths::addAll);
        return allPaths;
    }

    /**
     * Walks the {@code :children} hierarchy from {@code rootPath}, delivering each page's children to
     * the consumer as they arrive, and returns the total discovered.
     *
     * <p>
     * A hierarchy-enabled root lists every descendant in one response, so this normally makes a
     * single request and stops — a response carrying paths more than one level deep says so, and
     * descending into each of them would only re-fetch what is already in hand. When the model lists
     * direct children only, the descent continues per child, bounded by {@link #MAX_DEPTH} and by a
     * visited set that both dedupes the consumer's batches and keeps a self-referencing model from
     * looping.
     */
    public static long discoverPaths(DumAemConfiguration configuration, String rootPath,
            Consumer<List<String>> pageConsumer) {
        log.info("Delivery: discovering pages under '{}' via {}", rootPath,
                deliveryUrlPrefix(configuration));
        Set<String> visited = new LinkedHashSet<>();
        visited.add(rootPath);
        long discovered = descend(configuration, rootPath, pageConsumer, visited, 0);
        log.info("Delivery: discovered {} pages under '{}'", discovered, rootPath);
        return discovered;
    }

    private static long descend(DumAemConfiguration configuration, String path,
            Consumer<List<String>> pageConsumer, Set<String> visited, int depth) {
        if (depth > MAX_DEPTH) {
            log.warn("Delivery: depth limit {} reached at '{}'", MAX_DEPTH, path);
            return 0;
        }
        Optional<JSONObject> modelJson = getModelJson(path, configuration);
        if (modelJson.isEmpty()) {
            log.warn("Delivery: no model.json for '{}'", path);
            return 0;
        }
        List<String> descendants = descendantPaths(path, modelJson.get());
        List<String> fresh = descendants.stream().filter(visited::add).toList();
        if (fresh.isEmpty()) {
            return 0;
        }
        pageConsumer.accept(fresh);
        if (listsWholeHierarchy(path, descendants)) {
            return fresh.size();
        }
        long discovered = fresh.size();
        for (String child : fresh) {
            discovered += descend(configuration, child, pageConsumer, visited, depth + 1);
        }
        return discovered;
    }

    /** True when the model listed something below a direct child, i.e. the whole sub-tree. */
    private static boolean listsWholeHierarchy(String path, List<String> descendants) {
        int prefixLength = (path.endsWith("/") ? path : path + "/").length();
        return descendants.stream()
                .anyMatch(descendant -> descendant.indexOf('/', prefixLength) >= 0);
    }

    /**
     * The absolute descendant paths a page model declares under {@code :children}, restricted to
     * paths actually below {@code path} so a model echoing its own or a sibling's path is dropped.
     */
    static List<String> descendantPaths(String path, JSONObject modelJson) {
        JSONObject children = modelJson.optJSONObject(CHILDREN);
        if (children == null) {
            return List.of();
        }
        String prefix = path.endsWith("/") ? path : path + "/";
        List<String> paths = new ArrayList<>();
        for (String childPath : children.keySet()) {
            if (StringUtils.isNotBlank(childPath) && childPath.startsWith(prefix)) {
                paths.add(childPath);
            }
        }
        return paths;
    }

    /**
     * The <em>direct</em> child names under {@code path}: the first segment of each descendant path,
     * deduplicated, so a hierarchy root listing every descendant still yields one marker node per
     * direct child.
     */
    static Set<String> childNames(String path, JSONObject modelJson) {
        String prefix = path.endsWith("/") ? path : path + "/";
        Set<String> names = new LinkedHashSet<>();
        for (String childPath : descendantPaths(path, modelJson)) {
            String remainder = childPath.substring(prefix.length());
            int slash = remainder.indexOf('/');
            String name = slash < 0 ? remainder : remainder.substring(0, slash);
            if (StringUtils.isNotBlank(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * Formats {@code lastModifiedDate} — epoch milliseconds in the Core Components page model — into
     * the {@code .infinity.json} date format {@code DumAemObjectGeneric} parses. Returns {@code null}
     * when the model carries no usable value, which leaves the date unset rather than invented.
     */
    private static String formatModelDate(Object value) {
        Long epochMillis = asEpochMillis(value);
        if (epochMillis == null) {
            return null;
        }
        return MODEL_DATE_FORMATTER
                .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    private static Long asEpochMillis(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.isNumeric(text.trim())) {
            return Long.valueOf(text.trim());
        }
        return null;
    }

    private static void putIfPresent(JSONObject target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value);
        }
    }
}
