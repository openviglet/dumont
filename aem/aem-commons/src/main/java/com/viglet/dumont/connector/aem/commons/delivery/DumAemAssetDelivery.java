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
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.DAM_ASSET;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.METADATA;

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
 * Assets on the publish tier, through the Assets Search API (roadmap <strong>D67</strong>).
 *
 * <p>
 * The one delivery read that takes its query in a body rather than a URL, which is why D67 had to
 * grow {@link DumAemCommonsUtils#postResponseBody} first. It scopes by path, pages by cursor, and
 * returns each asset's metadata in the listing, so discovery and metadata are one request per page.
 *
 * <p>
 * <strong>On the response shape.</strong> Adobe has shipped this API under more than one field
 * spelling, so the reads here are deliberately tolerant: an item's path is taken from {@code path}
 * or {@code repo:path}, its title from {@code title}, {@code dc:title} or {@code name}, its
 * modification instant from {@code modified.at}, {@code modified} or {@code repo:modifyDate}, and
 * the collection from {@code items} or {@code results}. A response this cannot read yields
 * <em>nothing</em> — never a node with invented values — and the log names the endpoint that
 * answered, so a mismatch is diagnosable rather than silent. Verify against your own instance
 * before relying on it; if the endpoint differs, {@code assetSearchPath} on the source's
 * configuration points it elsewhere without a code change.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.7
 */
@Slf4j
public class DumAemAssetDelivery implements DumAemDeliverySurface {

    /** The API's default path on the delivery host. */
    static final String DEFAULT_SEARCH_PATH = "/adobe/assets/search";
    private static final String ITEMS = "items";
    private static final String RESULTS = "results";
    private static final String CURSOR = "cursor";
    private static final String PATH = "path";
    private static final String REPO_PATH = "repo:path";
    private static final String TITLE = "title";
    private static final String DC_TITLE = "dc:title";
    private static final String NAME = "name";
    private static final String MODIFIED = "modified";
    private static final String AT = "at";
    private static final String REPO_MODIFY_DATE = "repo:modifyDate";
    private static final int PAGE_SIZE = 100;
    /** Pages followed in one discovery, so a very large DAM drains predictably. */
    private static final int MAX_PAGES = 500;

    @Override
    public String getName() {
        return "asset";
    }

    /** A {@code dam:Asset} source that is not a Content Fragment source. */
    @Override
    public boolean supports(DumAemConfiguration configuration) {
        return DAM_ASSET.equals(configuration.getContentType())
                && !CONTENT_FRAGMENT.equals(configuration.getSubType());
    }

    @Override
    public long discoverPaths(DumAemConfiguration configuration, String rootPath,
            Consumer<List<String>> pageConsumer) {
        log.info("Delivery/asset: discovering assets under '{}' via {}", rootPath,
                DumAemDeliveryUtils.deliveryUrlPrefix(configuration));
        long discovered = 0;
        String cursor = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            Optional<JSONObject> response = search(configuration, rootPath, cursor);
            if (response.isEmpty()) {
                break;
            }
            List<String> paths = assetPaths(response.get(), rootPath);
            if (!paths.isEmpty()) {
                pageConsumer.accept(paths);
                discovered += paths.size();
            }
            cursor = response.get().optString(CURSOR, null);
            if (StringUtils.isBlank(cursor)) {
                break;
            }
        }
        log.info("Delivery/asset: discovered {} asset(s) under '{}'", discovered, rootPath);
        return discovered;
    }

    @Override
    public Optional<JSONObject> getContentNode(String path, DumAemConfiguration configuration) {
        return search(configuration, path, null)
                .flatMap(response -> itemAt(response, path))
                .map(DumAemAssetDelivery::toContentNode);
    }

    private static Optional<JSONObject> itemAt(JSONObject response, String path) {
        JSONArray items = items(response);
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && path.equals(itemPath(item))) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    private Optional<JSONObject> search(DumAemConfiguration configuration, String path,
            String cursor) {
        String url = DumAemDeliveryUtils.deliveryUrlPrefix(configuration) + searchPath(configuration);
        JSONObject body = new JSONObject().put(PATH, path).put("limit", PAGE_SIZE);
        if (StringUtils.isNotBlank(cursor)) {
            body.put(CURSOR, cursor);
        }
        Optional<JSONObject> response = DumAemCommonsUtils
                .postResponseBody(url, body.toString(), configuration)
                .filter(DumAemCommonsUtils::isResponseBodyJSONObject)
                .map(JSONObject::new);
        if (response.isEmpty()) {
            log.warn("Delivery/asset: no readable response from {} for path '{}'", url, path);
        }
        return response;
    }

    private static String searchPath(DumAemConfiguration configuration) {
        String configured = configuration.getAssetSearchPath();
        return StringUtils.isNotBlank(configured) ? configured : DEFAULT_SEARCH_PATH;
    }

    /** The asset paths in one search response, restricted to those at or below the root. */
    static List<String> assetPaths(JSONObject response, String rootPath) {
        JSONArray items = items(response);
        List<String> paths = new ArrayList<>(items.length());
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            String path = item != null ? itemPath(item) : null;
            if (StringUtils.isNotBlank(path) && path.startsWith(rootPath)) {
                paths.add(path);
            }
        }
        return paths;
    }

    /**
     * Translates one asset into the JCR node shape the connector parses: a {@code dam:Asset} whose
     * {@code jcr:content} carries the title, the dates and a {@code metadata} node — the address the
     * existing asset extraction already reads.
     */
    static JSONObject toContentNode(JSONObject asset) {
        return DumAemDeliveryNode.of(DAM_ASSET)
                .title(firstNonBlank(asset, TITLE, DC_TITLE, NAME))
                .lastModified(modifiedAt(asset))
                .contentNode(METADATA, metadata(asset))
                .build();
    }

    /**
     * The asset's own properties, minus the envelope fields the translation already consumed, so an
     * extension reading {@code jcr:content/metadata} sees what the API reported.
     */
    private static JSONObject metadata(JSONObject asset) {
        JSONObject metadata = asset.optJSONObject(METADATA);
        if (metadata != null) {
            return metadata;
        }
        JSONObject derived = new JSONObject();
        for (String key : asset.keySet()) {
            if (!PATH.equals(key) && !REPO_PATH.equals(key) && !CURSOR.equals(key)) {
                derived.put(key, asset.get(key));
            }
        }
        return derived;
    }

    private static JSONArray items(JSONObject response) {
        JSONArray items = response.optJSONArray(ITEMS);
        if (items == null) {
            items = response.optJSONArray(RESULTS);
        }
        return items != null ? items : new JSONArray();
    }

    private static String itemPath(JSONObject item) {
        return firstNonBlank(item, PATH, REPO_PATH);
    }

    private static String modifiedAt(JSONObject asset) {
        JSONObject modified = asset.optJSONObject(MODIFIED);
        if (modified != null) {
            return modified.optString(AT, null);
        }
        return firstNonBlank(asset, MODIFIED, REPO_MODIFY_DATE);
    }

    private static String firstNonBlank(JSONObject json, String... keys) {
        for (String key : keys) {
            String value = json.optString(key, null);
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
