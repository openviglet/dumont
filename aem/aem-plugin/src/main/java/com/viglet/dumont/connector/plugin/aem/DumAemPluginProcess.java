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

package com.viglet.dumont.connector.plugin.aem;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.plugin.aem.api.DumAemAttributeIndex;
import com.viglet.dumont.connector.plugin.aem.api.DumAemPathList;
import com.viglet.dumont.connector.plugin.aem.command.IndexingCommand;
import com.viglet.dumont.connector.plugin.aem.command.IndexingCommandFactory;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.executor.IndexingExecutor;
import com.viglet.dumont.connector.plugin.aem.handler.DependencyHandler;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSessionService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSourceService;

import lombok.extern.slf4j.Slf4j;

/**
 * Main entry point for AEM plugin indexing operations.
 * Coordinates indexing using Command, Factory, and Executor patterns
 * for improved scalability and maintainability.
 * 
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
@Component
public class DumAemPluginProcess {

        private final DumAemSourceService sourceService;
        private final DumAemSessionService sessionService;
        private final IndexingCommandFactory commandFactory;
        private final IndexingExecutor executor;
        private final DependencyHandler dependencyHandler;

        public DumAemPluginProcess(
                        DumAemSourceService sourceService,
                        DumAemSessionService sessionService,
                        IndexingCommandFactory commandFactory,
                        IndexingExecutor executor,
                        DependencyHandler dependencyHandler) {
                this.sourceService = sourceService;
                this.sessionService = sessionService;
                this.commandFactory = commandFactory;
                this.executor = executor;
                this.dependencyHandler = dependencyHandler;
        }

        /**
         * Indexes all content from a source by name (async).
         * 
         * @param sourceName the source name
         */
        @Async
        public void indexAllByNameAsync(String sourceName) {
                sourceService.getDumAemSourceByName(sourceName)
                                .ifPresentOrElse(
                                                this::indexAll,
                                                () -> log.error("Source '{}' not found", sourceName));
        }

        /**
         * Indexes all content from a source by ID (async).
         * 
         * @param id the source ID
         */
        @Async
        public void indexAllByIdAsync(String id) {
                sourceService.getDumAemSourceById(id)
                                .ifPresentOrElse(
                                                this::indexAll,
                                                () -> log.error("Source with ID '{}' not found", id));
        }

        /**
         * Indexes specific paths from a source (async).
         * 
         * @param source   the source name
         * @param pathList the list of paths to index
         */
        @Async
        public void sentToIndexStandaloneAsync(@NotNull String source, @NotNull DumAemPathList pathList) {
                sentToIndexStandalone(source, pathList);
        }

        /**
         * Indexes specific paths from a source (sync).
         * 
         * @param source   the source name
         * @param pathList the list of paths to index
         */
        public void sentToIndexStandalone(@NotNull String source, @NotNull DumAemPathList pathList) {
                if (CollectionUtils.isEmpty(pathList.getPaths())) {
                        log.warn("Received empty path list for source '{}'", source);
                        return;
                }

                DumAemAttributeIndex attribute = pathList.getAttribute();
                DumAemPathList effectivePathList = pathList;

                if (attribute == DumAemAttributeIndex.URL) {
                        List<String> ids = convertUrl2Id(source, pathList);
                        if (CollectionUtils.isEmpty(ids)) {
                                log.warn("No IDs resolved from URLs for source '{}'", source);
                                return;
                        }
                        effectivePathList = DumAemPathList.builder()
                                        .attribute(DumAemAttributeIndex.ID)
                                        .paths(ids)
                                        .event(pathList.getEvent())
                                        .recursive(pathList.getRecursive())
                                        .build();
                }

                indexStandalonePaths(source, effectivePathList);
        }

        private void indexStandalonePaths(String source, DumAemPathList pathList) {
                int pathCount = CollectionUtils.size(pathList.getPaths());
                log.info("Processing {} path(s) for source '{}'", pathCount, source);

                sourceService.getDumAemSourceByName(source)
                                .ifPresentOrElse(
                                                dumAemSource -> executeStandaloneIndexing(dumAemSource, pathList),
                                                () -> log.error("Source '{}' not found", source));
        }

        /**
         * Converts a list of URLs to their corresponding IDs for a given source.
         * Returns an empty list if conversion fails.
         */
        private List<String> convertUrl2Id(@NotNull String source, @NotNull DumAemPathList pathList) {
                List<String> urls = pathList.getPaths();
                if (CollectionUtils.isEmpty(urls)) {
                        return List.of();
                }
                return urls.stream()
                                .map(url -> sourceService.resolveIdFromUrl(source, url))
                                .filter(id -> id != null && !id.isBlank())
                                .toList();
        }

        /**
         * Indexes all content from a source with exclusive lock.
         * 
         * @param source the AEM source configuration
         */
        public void indexAll(DumAemSource source) {
                DumAemSession session = sessionService.getDumAemSession(source, false);
                IndexingCommand command = commandFactory.createIndexAllCommand(session);
                executor.executeExclusive(command);
        }

        /**
         * Executes standalone indexing for specific paths.
         */
        private void executeStandaloneIndexing(DumAemSource source, DumAemPathList pathList) {
                DumAemSession session = sessionService.getDumAemSession(source, pathList, true);
                List<String> paths = pathList.getPaths();

                // Create and execute the indexing command
                IndexingCommand command = commandFactory.createIndexPathsCommand(session, paths);
                executor.executeStandalone(command);

                // Process dependencies after main indexing
                dependencyHandler.processDependencies(session, paths);
        }

        /**
         * Checks if a source is currently being indexed exclusively.
         * 
         * @param sourceName the source name
         * @return true if the source is running
         */
        public boolean isSourceRunning(String sourceName) {
                return executor.isRunning(sourceName);
        }

        /**
         * Gets the count of currently running exclusive indexing processes.
         * 
         * @return the count
         */
        public int getRunningSourcesCount() {
                return executor.getRunningCount();
        }
}
