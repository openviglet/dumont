/*
 *
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

package com.viglet.dumont.connector.commons.plugin;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public interface DumConnectorPlugin {

    record EnvironmentInfo(String environment, List<String> sites) {}

    void crawl();

    /**
     * The sources a scheduled crawl starts one at a time through {@link #indexAll(String)}, each
     * under the same run lock an on-demand run takes (D128). Empty, the default, means the plugin
     * crawls on its own and the scheduler calls {@link #crawl()} instead.
     */
    default Collection<String> scheduledSources() {
        return List.of();
    }

    String getProviderName();

    /**
     * Whether this plugin owns (manages) the given source. Used to route connector-level
     * requests to the correct plugin when several connector plugins are active in the same
     * application instance. Each plugin answers by looking the source up in its own store.
     *
     * @param source the source name/id as received by the connector API
     * @return {@code true} if this plugin manages the source; {@code false} by default
     */
    default boolean ownsSource(String source) {
        return false;
    }

    void indexAll(String source);

    void indexById(String source, List<String> contentId);

    List<String> discoverContentIds(String source);

    default Locale resolveLocale(String source, String objectId) {
        return null;
    }

    default List<EnvironmentInfo> resolveEnvironments(String source, String objectId) {
        return List.of();
    }
}
