/*
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

package com.viglet.dumont.connector.plugin.aem.handler;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.plugin.aem.command.IndexingCommand;
import com.viglet.dumont.connector.plugin.aem.command.IndexingCommandFactory;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.service.DumAemService;

import lombok.extern.slf4j.Slf4j;

/**
 * Handles dependency resolution and re-indexing.
 * When content is updated, this handler finds and re-indexes dependent content.
 * 
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
@Component
public class DependencyHandler {

    private final DumConnectorContext connectorContext;
    private final DumAemService aemService;
    private final IndexingCommandFactory commandFactory;
    private final boolean dependenciesEnabled;

    public DependencyHandler(
            DumConnectorContext connectorContext,
            DumAemService aemService,
            IndexingCommandFactory commandFactory,
            @Value("${dumont.dependencies.enabled:true}") boolean dependenciesEnabled) {
        this.connectorContext = connectorContext;
        this.aemService = aemService;
        this.commandFactory = commandFactory;
        this.dependenciesEnabled = dependenciesEnabled;
    }

    /**
     * Processes dependencies for the given paths.
     * Finds content that depends on the specified paths and re-indexes it.
     * 
     * @param session the AEM session
     * @param paths   the list of paths that were updated
     */
    public void processDependencies(DumAemSession session, List<String> paths) {
        if (!dependenciesEnabled) {
            log.debug("Dependency processing is disabled");
            return;
        }

        if (paths == null || paths.isEmpty()) {
            log.debug("No paths provided for dependency processing");
            return;
        }

        List<String> dependentIds = connectorContext.getObjectIdByDependency(
                session.getSource(),
                aemService.getProviderName(),
                paths);

        if (dependentIds.isEmpty()) {
            log.debug("No dependencies found for {} paths", paths.size());
            return;
        }

        log.info("Processing {} dependencies for source: {}", dependentIds.size(), session.getSource());

        IndexingCommand command = commandFactory.createIndexPathsCommand(session, dependentIds);
        command.execute();
    }

    /**
     * Checks if dependency processing is enabled.
     * 
     * @return true if enabled
     */
    public boolean isDependenciesEnabled() {
        return dependenciesEnabled;
    }
}
