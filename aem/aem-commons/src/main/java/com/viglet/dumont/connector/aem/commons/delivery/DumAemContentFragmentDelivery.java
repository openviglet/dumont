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

import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CONTENT_FRAGMENT;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_MODEL;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.DAM_ASSET;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.DATA_FOLDER;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.dumont.connector.aem.commons.utils.DumAemDeliveryUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Content Fragments on the publish tier, through the Content Fragment OpenAPI (roadmap
 * <strong>D67</strong>).
 *
 * <p>
 * A fragment source used to be reachable only through author: {@code .model.json} says nothing about
 * one, and {@code /bin/querybuilder.json} is not served on publish. The Content Fragment API is —
 * it lists fragments under a path, cursor-paginated, and returns each fragment's model and field
 * values in the same response, so discovery and extraction are one request per page rather than one
 * per fragment.
 *
 * <p>
 * The translation puts the field values under {@code jcr:content/data/master}, which is where the
 * connector already looks for them: {@code DumAemObjectGeneric.getDataFolder} reads the model from
 * {@code data/cq:model} and {@code setDataPath("data/master")} lifts the values into attributes. So
 * an extension written against a fragment indexed from author needs no change.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.7
 */
@Slf4j
public class DumAemContentFragmentDelivery implements DumAemDeliverySurface {

    /** The API's own path on the delivery host. Overridable per source for a non-default mount. */
    static final String FRAGMENTS_PATH = "/adobe/sites/cf/fragments";
    private static final String ITEMS = "items";
    private static final String CURSOR = "cursor";
    private static final String PATH = "path";
    private static final String TITLE = "title";
    private static final String MODEL_ID = "modelId";
    private static final String MODIFIED = "modified";
    private static final String AT = "at";
    private static final String FIELDS = "fields";
    private static final String MASTER = "master";
    /** Pages followed in one discovery, so a very large fragment library drains predictably. */
    private static final int MAX_PAGES = 500;

    @Override
    public String getName() {
        return "content-fragment";
    }

    /** A {@code dam:Asset} source whose sub-type says its assets are Content Fragments. */
    @Override
    public boolean supports(DumAemConfiguration configuration) {
        return DAM_ASSET.equals(configuration.getContentType())
                && CONTENT_FRAGMENT.equals(configuration.getSubType());
    }

    @Override
    public long discoverPaths(DumAemConfiguration configuration, String rootPath,
            Consumer<List<String>> pageConsumer) {
        log.info("Delivery/content-fragment: discovering fragments under '{}' via {}", rootPath,
                DumAemDeliveryUtils.deliveryUrlPrefix(configuration));
        long discovered = 0;
        String cursor = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            Optional<JSONObject> response = listFragments(configuration, rootPath, cursor);
            if (response.isEmpty()) {
                break;
            }
            List<String> paths = fragmentPaths(response.get(), rootPath);
            if (!paths.isEmpty()) {
                pageConsumer.accept(paths);
                discovered += paths.size();
            }
            cursor = response.get().optString(CURSOR, null);
            if (StringUtils.isBlank(cursor)) {
                break;
            }
        }
        log.info("Delivery/content-fragment: discovered {} fragment(s) under '{}'", discovered,
                rootPath);
        return discovered;
    }

    @Override
    public Optional<JSONObject> getContentNode(String path, DumAemConfiguration configuration) {
        return fetchFragment(path, configuration).map(DumAemContentFragmentDelivery::toContentNode);
    }

    /**
     * The single fragment at {@code path}. The API lists by path, so this reads the first item whose
     * path matches exactly rather than assuming a by-path read endpoint exists.
     */
    private Optional<JSONObject> fetchFragment(String path, DumAemConfiguration configuration) {
        return listFragments(configuration, path, null).flatMap(response -> {
            JSONArray items = response.optJSONArray(ITEMS);
            if (items == null) {
                return Optional.empty();
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null && path.equals(item.optString(PATH, null))) {
                    return Optional.of(item);
                }
            }
            log.warn("Delivery/content-fragment: no fragment at '{}'", path);
            return Optional.empty();
        });
    }

    private Optional<JSONObject> listFragments(DumAemConfiguration configuration, String path,
            String cursor) {
        StringBuilder url = new StringBuilder(DumAemDeliveryUtils.deliveryUrlPrefix(configuration))
                .append(FRAGMENTS_PATH).append("?path=").append(path);
        if (StringUtils.isNotBlank(cursor)) {
            url.append("&cursor=").append(cursor);
        }
        try {
            return DumAemCommonsUtils.getResponseBody(url.toString(), configuration, false)
                    .filter(DumAemCommonsUtils::isResponseBodyJSONObject)
                    .map(JSONObject::new);
        } catch (IOException e) {
            log.error("Delivery/content-fragment: request failed for {}: {}", url, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /** The fragment paths in one listing response, restricted to those at or below the root. */
    static List<String> fragmentPaths(JSONObject response, String rootPath) {
        JSONArray items = response.optJSONArray(ITEMS);
        if (items == null) {
            return List.of();
        }
        List<String> paths = new ArrayList<>(items.length());
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            String path = item != null ? item.optString(PATH, null) : null;
            if (StringUtils.isNotBlank(path) && path.startsWith(rootPath)) {
                paths.add(path);
            }
        }
        return paths;
    }

    /**
     * Translates one fragment into the JCR node shape the connector parses: a {@code dam:Asset}
     * flagged {@code contentFragment}, with the model under {@code data/cq:model} and the field
     * values under {@code data/master} — the two addresses the existing extraction reads.
     */
    static JSONObject toContentNode(JSONObject fragment) {
        JSONObject master = new JSONObject();
        JSONObject fields = fragment.optJSONObject(FIELDS);
        if (fields != null) {
            for (String name : fields.keySet()) {
                master.put(name, fields.get(name));
            }
        }
        JSONObject data = new JSONObject().put(MASTER, master);
        String modelId = fragment.optString(MODEL_ID, null);
        if (StringUtils.isNotBlank(modelId)) {
            data.put(CQ_MODEL, modelId);
        }
        return DumAemDeliveryNode.of(DAM_ASSET)
                .title(fragment.optString(TITLE, null))
                .contentFlag(CONTENT_FRAGMENT, true)
                .lastModified(modifiedAt(fragment))
                .contentNode(DATA_FOLDER, data)
                .build();
    }

    /**
     * The fragment's modification instant. The API reports it as {@code modified.at}; a flat
     * {@code modified} string is accepted too, so a response shaped either way still carries a date
     * rather than silently losing the delta signal.
     */
    private static String modifiedAt(JSONObject fragment) {
        JSONObject modified = fragment.optJSONObject(MODIFIED);
        if (modified != null) {
            return modified.optString(AT, null);
        }
        return fragment.optString(MODIFIED, null);
    }
}
