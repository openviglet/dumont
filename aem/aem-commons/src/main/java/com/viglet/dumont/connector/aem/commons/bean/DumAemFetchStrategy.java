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

package com.viglet.dumont.connector.aem.commons.bean;

import org.apache.commons.lang3.StringUtils;

/**
 * How a source's content is fetched from AEM (roadmap <strong>D61</strong>).
 *
 * <p>
 * {@link #SLING} is the historical behaviour and stays the default: discovery through
 * {@code /bin/querybuilder.json} and traversal / metadata through {@code .infinity.json}. Both are
 * author-tier Sling servlets — on AEM as a Cloud Service the publish tier does not serve them, so a
 * Cloud Service source can only be reached that way through author, with an authoring credential.
 *
 * <p>
 * {@link #DELIVERY} reaches the same content through the published delivery surface instead:
 * {@code .model.json} on the publish (or {@code delivery-pXXXX-eYYYY}) host, which is CDN-fronted and
 * publish-only. It is selected per source, so on-prem and AMS keep the traversal that works there.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum DumAemFetchStrategy {

    /** Author-tier Sling servlets: {@code .infinity.json} + {@code /bin/querybuilder.json}. */
    SLING,

    /** Publish-tier delivery APIs: {@code .model.json} on the publish / delivery host. */
    DELIVERY;

    /**
     * Parses a configured strategy name, case-insensitively, falling back to {@link #SLING} for a
     * blank or unknown value so an unrecognised configuration never silently stops indexing.
     */
    public static DumAemFetchStrategy from(String value) {
        if (StringUtils.isBlank(value)) {
            return SLING;
        }
        for (DumAemFetchStrategy strategy : values()) {
            if (strategy.name().equalsIgnoreCase(value.trim())) {
                return strategy;
            }
        }
        return SLING;
    }
}
