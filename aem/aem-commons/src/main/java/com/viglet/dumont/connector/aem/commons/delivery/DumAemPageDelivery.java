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

package com.viglet.dumont.connector.aem.commons.delivery;

import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_PAGE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.dumont.connector.aem.commons.utils.DumAemDeliveryUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Pages on the publish tier, through {@code .model.json} (roadmap <strong>D61</strong>, lifted
 * behind the surface seam by <strong>D67</strong>).
 *
 * <p>
 * {@code .model.json} is served by Core Components on publish, is CDN-fronted, and carries both the
 * page's own metadata and — for a hierarchy root — every descendant under {@code :children}. That is
 * enough for the two things the author servlets were doing: traversal and metadata.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.7
 */
@Slf4j
public class DumAemPageDelivery implements DumAemDeliverySurface {

    private static final String MODEL_JSON = ".model.json";
    private static final String CHILDREN = ":children";
    private static final String TITLE = "title";
    private static final String TEMPLATE_NAME = "templateName";
    private static final String LAST_MODIFIED_DATE = "lastModifiedDate";
    private static final String CQ_TEMPLATE = "cq:template";
    /** Depth guard for the {@code :children} descent; a hierarchy root usually needs one level. */
    private static final int MAX_DEPTH = 20;

    @Override
    public String getName() {
        return "page";
    }

    /** The default surface: anything that is not an asset source is read as pages. */
    @Override
    public boolean supports(DumAemConfiguration configuration) {
        return true;
    }

    /**
     * Fetches the raw {@code .model.json} for a path from the delivery host, with a
     * {@code ?t=<timestamp>} cache-buster so a CDN hit never hides a republish.
     */
    public Optional<JSONObject> getModelJson(String path, DumAemConfiguration configuration) {
        String url = "%s%s%s?t=%d".formatted(DumAemDeliveryUtils.deliveryUrlPrefix(configuration),
                path, MODEL_JSON, System.currentTimeMillis());
        try {
            return DumAemCommonsUtils.getResponseBody(url, configuration, false)
                    .filter(DumAemCommonsUtils::isResponseBodyJSONObject)
                    .map(JSONObject::new);
        } catch (IOException e) {
            log.error("Delivery: model.json request failed for {}: {}", url, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<JSONObject> getContentNode(String path, DumAemConfiguration configuration) {
        return getModelJson(path, configuration).map(modelJson -> toContentNode(path, modelJson));
    }

    /**
     * Translates a Core Components page model into the {@code .infinity.json} node shape.
     *
     * <p>
     * Direct child pages derived from {@code :children} are added as marker nodes under their
     * <em>relative</em> name, which is what lets the existing traversal keep walking — it re-fetches
     * each child by path and lands back here.
     */
    public static JSONObject toContentNode(String path, JSONObject modelJson) {
        DumAemDeliveryNode node = DumAemDeliveryNode.of(CQ_PAGE)
                .title(modelJson.optString(TITLE, null))
                .contentProperty(CQ_TEMPLATE, modelJson.optString(TEMPLATE_NAME, null))
                .lastModified(asEpochMillis(modelJson.opt(LAST_MODIFIED_DATE)));
        for (String childName : childNames(path, modelJson)) {
            node.child(childName, new JSONObject().put("jcr:primaryType", CQ_PAGE));
        }
        return node.build();
    }

    /**
     * Walks the {@code :children} hierarchy from {@code rootPath}.
     *
     * <p>
     * A hierarchy-enabled root lists every descendant in one response, so this normally makes a
     * single request and stops — a response carrying paths more than one level deep says so, and
     * descending into each of them would only re-fetch what is already in hand.
     */
    @Override
    public long discoverPaths(DumAemConfiguration configuration, String rootPath,
            Consumer<List<String>> pageConsumer) {
        log.info("Delivery/page: discovering pages under '{}' via {}", rootPath,
                DumAemDeliveryUtils.deliveryUrlPrefix(configuration));
        Set<String> visited = new LinkedHashSet<>();
        visited.add(rootPath);
        long discovered = descend(configuration, rootPath, pageConsumer, visited, 0);
        log.info("Delivery/page: discovered {} pages under '{}'", discovered, rootPath);
        return discovered;
    }

    private long descend(DumAemConfiguration configuration, String path,
            Consumer<List<String>> pageConsumer, Set<String> visited, int depth) {
        if (depth > MAX_DEPTH) {
            log.warn("Delivery/page: depth limit {} reached at '{}'", MAX_DEPTH, path);
            return 0;
        }
        Optional<JSONObject> modelJson = getModelJson(path, configuration);
        if (modelJson.isEmpty()) {
            log.warn("Delivery/page: no model.json for '{}'", path);
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
    public static List<String> descendantPaths(String path, JSONObject modelJson) {
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
    public static Set<String> childNames(String path, JSONObject modelJson) {
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

    private static Long asEpochMillis(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            return StringUtils.isNumeric(text.trim()) ? Long.valueOf(text.trim())
                    : DumAemDeliveryNode.epochMillis(text);
        }
        return null;
    }
}
