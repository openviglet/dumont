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

package com.viglet.dumont.connector.plugin.aem.command.impl;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;

import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.dumont.connector.aem.commons.utils.DumAemQueryBuilderUtils;
import com.viglet.dumont.connector.plugin.aem.command.IndexingCommand;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.navigator.AemNodeNavigator;

import lombok.extern.slf4j.Slf4j;

/**
 * Command for indexing all content using the AEM QueryBuilder API for discovery
 * and infinity.json for content fetching.
 * <p>
 * Pipeline model: each page of discovered paths is processed in parallel
 * immediately, without accumulating all paths in memory first.
 *
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
public class IndexAllQueryBuilderCommand implements IndexingCommand {

    private final DumAemSession session;
    private final AemNodeNavigator nodeNavigator;
    private final int parallelism;

    public IndexAllQueryBuilderCommand(DumAemSession session, AemNodeNavigator nodeNavigator,
            int parallelism) {
        this.session = session;
        this.nodeNavigator = nodeNavigator;
        this.parallelism = Math.max(1, parallelism);
    }

    @Override
    public void execute() {
        log.info("Executing IndexAll (QueryBuilder pipeline) for source: {} (parallelism={})",
                session.getSource(), parallelism);

        if (!DumAemCommonsUtils.usingContentTypeParameter(session.getConfiguration())) {
            log.warn("Content type parameter not configured for source: {}",
                    session.getSource());
            return;
        }

        AtomicLong processed = new AtomicLong();

        try (ForkJoinPool pool = new ForkJoinPool(parallelism)) {
            long total = DumAemQueryBuilderUtils.discoverPaths(
                    session.getConfiguration(),
                    pagePaths -> processPage(pool, pagePaths, processed));

            if (total == 0) {
                log.warn("QueryBuilder returned no paths for source: {}",
                        session.getSource());
            } else {
                log.info("QueryBuilder pipeline completed: {}/{} paths processed for source: {}",
                        processed.get(), total, session.getSource());
            }
        }
    }

    private void processPage(ForkJoinPool pool, List<String> paths, AtomicLong processed) {
        try {
            pool.submit(() -> paths.parallelStream()
                    .forEach(path -> {
                        processPath(path);
                        processed.incrementAndGet();
                    })).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Parallel processing interrupted for source: {}",
                    session.getSource(), e);
        } catch (Exception e) {
            log.error("Error during parallel processing for source: {}",
                    session.getSource(), e);
        }
    }

    private void processPath(String path) {
        try {
            DumAemCommonsUtils.getInfinityJson(path, session.getConfiguration(), false)
                    .ifPresentOrElse(
                            nodeJson -> nodeNavigator.processNode(session, path,
                                    nodeJson),
                            () -> log.warn("No content found for path: {}", path));
        } catch (Exception e) {
            log.error("Error processing path '{}': {}", path, e.getMessage(), e);
        }
    }

    @Override
    public DumAemSession getSession() {
        return session;
    }

    @Override
    public String getDescription() {
        return "IndexAllQueryBuilder[source=%s]".formatted(session.getSource());
    }
}
