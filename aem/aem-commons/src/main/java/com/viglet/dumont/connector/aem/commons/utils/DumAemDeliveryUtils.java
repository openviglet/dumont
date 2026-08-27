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

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.delivery.DumAemAssetDelivery;
import com.viglet.dumont.connector.aem.commons.delivery.DumAemContentFragmentDelivery;
import com.viglet.dumont.connector.aem.commons.delivery.DumAemDeliverySurface;
import com.viglet.dumont.connector.aem.commons.delivery.DumAemPageDelivery;

import lombok.extern.slf4j.Slf4j;

/**
 * The publish-tier fetch strategy: picks the delivery surface a source's content is read from and
 * delegates to it (roadmap <strong>D61</strong>, one surface per content kind since
 * <strong>D67</strong>).
 *
 * <p>
 * D61 read everything through {@code .model.json}, which serves pages and nothing else — a Content
 * Fragment or an asset source still had to go through author. The choice of surface comes from the
 * source's own {@code contentType} / {@code subType}, so nothing new has to be configured: an
 * existing source keeps working and a fragment source stops needing an authoring credential.
 *
 * <p>
 * Every caller still reaches this class, and every surface owes the same translation — the
 * {@code .infinity.json} node shape {@code DumAemObjectGeneric} parses — so the change stays behind
 * the fetch.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public class DumAemDeliveryUtils {

    /**
     * Ordered by specificity: the page surface accepts anything, so it is last and acts as the
     * default. Stateless, therefore shared.
     */
    private static final List<DumAemDeliverySurface> SURFACES = List.of(
            new DumAemContentFragmentDelivery(),
            new DumAemAssetDelivery(),
            new DumAemPageDelivery());

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

    /** The surface this source reads from — the first that claims it. */
    public static DumAemDeliverySurface surfaceFor(DumAemConfiguration configuration) {
        return SURFACES.stream()
                .filter(surface -> surface.supports(configuration))
                .findFirst()
                // Unreachable while the page surface accepts everything; stated rather than assumed.
                .orElseThrow(() -> new IllegalStateException(
                        "No delivery surface for contentType=" + configuration.getContentType()));
    }

    /**
     * The delivery-tier stand-in for {@code .infinity.json}: the content's metadata translated into
     * the JCR node shape the rest of the connector parses.
     */
    public static Optional<JSONObject> getContentNode(String path,
            DumAemConfiguration configuration) {
        return surfaceFor(configuration).getContentNode(path, configuration);
    }

    /**
     * The raw {@code .model.json} for a path, used by the model.json extraction base rather than by
     * the fetch seam. Unlike {@link #getContentNode} this is page-shaped by definition, so it does
     * not route.
     */
    public static Optional<JSONObject> getModelJson(String path,
            DumAemConfiguration configuration) {
        return new DumAemPageDelivery().getModelJson(path, configuration);
    }

    /** Discovers every content path under the configured root and collects them into a list. */
    public static List<String> queryAllPaths(DumAemConfiguration configuration) {
        DumAemDeliverySurface surface = surfaceFor(configuration);
        log.info("Delivery: '{}' surface answering for contentType={} subType={}",
                surface.getName(), configuration.getContentType(), configuration.getSubType());
        return surface.queryAllPaths(configuration);
    }

    /**
     * Discovers content paths under {@code rootPath}, delivering each batch to the consumer as it
     * arrives, and returns the total discovered.
     */
    public static long discoverPaths(DumAemConfiguration configuration, String rootPath,
            Consumer<List<String>> pageConsumer) {
        return surfaceFor(configuration).discoverPaths(configuration, rootPath, pageConsumer);
    }
}
