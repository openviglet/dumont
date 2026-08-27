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

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.json.JSONObject;

import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;

/**
 * One publish-tier API a source's content can be read from (roadmap <strong>D67</strong>).
 *
 * <p>
 * D61 established the fetch strategy — author servlets or publish delivery — and gave the delivery
 * side a single implementation, {@code .model.json}, which serves pages and nothing else. A Content
 * Fragment source gets nothing from it, and neither does an asset source, so both still had to be
 * indexed through author. This is the seam that lets the delivery side have one implementation per
 * kind of content, selected from the source's own {@code contentType} / {@code subType} rather than
 * from a new setting the operator has to know about.
 *
 * <p>
 * Every implementation owes the same translation D61 wrote: produce the {@code .infinity.json} node
 * shape {@code DumAemObjectGeneric} already parses. That is what keeps the change confined to the
 * fetch — extraction, delta, dependency harvest and the job pipeline are untouched.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.7
 */
public interface DumAemDeliverySurface {

    /** Whether this surface is the one for the given source. */
    boolean supports(DumAemConfiguration configuration);

    /**
     * Discovers every content path under {@code rootPath}, delivering each batch to the consumer as
     * it arrives, and returns the total discovered.
     */
    long discoverPaths(DumAemConfiguration configuration, String rootPath,
            Consumer<List<String>> pageConsumer);

    /**
     * One content's metadata, translated into the JCR node shape the connector parses. Empty when
     * the delivery tier does not answer for that path — never a fabricated node.
     */
    Optional<JSONObject> getContentNode(String path, DumAemConfiguration configuration);

    /** A short name for logs, so a run says which surface answered. */
    String getName();

    /** Collects {@link #discoverPaths} into a list, for the callers that need the whole set. */
    default List<String> queryAllPaths(DumAemConfiguration configuration) {
        List<String> allPaths = new java.util.ArrayList<>();
        discoverPaths(configuration, configuration.getRootPath(), allPaths::addAll);
        return allPaths;
    }
}
