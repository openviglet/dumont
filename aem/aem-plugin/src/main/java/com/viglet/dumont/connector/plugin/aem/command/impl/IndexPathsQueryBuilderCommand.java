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

import org.apache.commons.lang3.StringUtils;

import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.dumont.connector.aem.commons.utils.DumAemQueryBuilderUtils;
import com.viglet.dumont.connector.plugin.aem.command.IndexingCommand;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.navigator.AemNodeNavigator;
import com.viglet.dumont.connector.plugin.aem.service.DumAemJobService;

import lombok.extern.slf4j.Slf4j;

/**
 * Command for recursive standalone indexing using the AEM QueryBuilder API
 * for descendant discovery and parallel processing.
 * <p>
 * For each input path, descendants matching the configured content type are
 * discovered in pages and processed in parallel via a {@link ForkJoinPool}.
 * Mirrors the pipeline used by {@link IndexAllQueryBuilderCommand} but scoped
 * to the input subtree.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Slf4j
public class IndexPathsQueryBuilderCommand implements IndexingCommand {

    private final DumAemSession session;
    private final List<String> paths;
    private final AemNodeNavigator nodeNavigator;
    private final DumAemJobService jobService;
    private final int parallelism;

    public IndexPathsQueryBuilderCommand(DumAemSession session, List<String> paths,
            AemNodeNavigator nodeNavigator, DumAemJobService jobService, int parallelism) {
        this.session = session;
        this.paths = paths;
        this.nodeNavigator = nodeNavigator;
        this.jobService = jobService;
        this.parallelism = Math.max(1, parallelism);
    }

    @Override
    public void execute() {
        log.info("Executing IndexPaths (QueryBuilder pipeline) for {} root path(s) in source: {} (parallelism={})",
                paths.size(), session.getSource(), parallelism);

        if (!DumAemCommonsUtils.usingContentTypeParameter(session.getConfiguration())) {
            log.warn("Content type parameter not configured for source: {}", session.getSource());
            return;
        }

        AtomicLong processed = new AtomicLong();

        try (ForkJoinPool pool = new ForkJoinPool(parallelism)) {
            paths.stream()
                    .filter(StringUtils::isNotBlank)
                    .forEach(rootPath -> indexSubtree(pool, rootPath, processed));
        }

        log.info("QueryBuilder standalone pipeline completed: {} paths processed for source: {}",
                processed.get(), session.getSource());
    }

    private void indexSubtree(ForkJoinPool pool, String rootPath, AtomicLong processed) {
        long total = DumAemQueryBuilderUtils.discoverPaths(
                session.getConfiguration(),
                rootPath,
                pagePaths -> processPage(pool, pagePaths, processed));

        if (total == 0) {
            log.warn("QueryBuilder returned no paths under '{}' for source: {}",
                    rootPath, session.getSource());
        } else {
            log.info("QueryBuilder discovered {} paths under '{}' for source: {}",
                    total, rootPath, session.getSource());
        }
    }

    private void processPage(ForkJoinPool pool, List<String> pagePaths, AtomicLong processed) {
        try {
            pool.submit(() -> pagePaths.parallelStream()
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
            if (session.getEvent() == DumAemEvent.DEINDEXING) {
                jobService.createDeIndexJobAndSendToConnectorQueue(session, path);
                return;
            }

            DumAemCommonsUtils.getInfinityJson(path, session.getConfiguration(), false)
                    .ifPresentOrElse(
                            nodeJson -> nodeNavigator.processNode(session, path, nodeJson),
                            () -> jobService.createDeIndexJobAndSendToConnectorQueue(session, path));
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
        return "IndexPathsQueryBuilder[source=%s, rootPathCount=%d]"
                .formatted(session.getSource(), paths.size());
    }
}
