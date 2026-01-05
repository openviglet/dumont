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

package com.viglet.dumont.connector.plugin.aem.command;

import java.util.List;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.plugin.aem.command.impl.IndexAllCommand;
import com.viglet.dumont.connector.plugin.aem.command.impl.IndexPathsCommand;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.navigator.AemNodeNavigator;
import com.viglet.dumont.connector.plugin.aem.service.DumAemJobService;

/**
 * Factory for creating indexing commands.
 * Centralizes command creation and ensures proper dependency injection.
 * 
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Component
public class IndexingCommandFactory {

    private final AemNodeNavigator nodeNavigator;
    private final DumAemJobService jobService;

    public IndexingCommandFactory(AemNodeNavigator nodeNavigator, DumAemJobService jobService) {
        this.nodeNavigator = nodeNavigator;
        this.jobService = jobService;
    }

    /**
     * Creates a command to index all content from a source.
     * 
     * @param session the AEM session
     * @return the index all command
     */
    public IndexingCommand createIndexAllCommand(DumAemSession session) {
        return new IndexAllCommand(session, nodeNavigator);
    }

    /**
     * Creates a command to index specific paths.
     * 
     * @param session the AEM session
     * @param paths   the list of paths to index
     * @return the index paths command
     */
    public IndexingCommand createIndexPathsCommand(DumAemSession session, List<String> paths) {
        return new IndexPathsCommand(session, paths, nodeNavigator, jobService);
    }
}
