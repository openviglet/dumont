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

package com.viglet.dumont.connector.plugin.aem.command.impl;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.dumont.connector.plugin.aem.command.IndexingCommand;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.navigator.AemNodeNavigator;
import com.viglet.dumont.connector.plugin.aem.service.DumAemJobService;

import lombok.extern.slf4j.Slf4j;

/**
 * Command for indexing specific paths.
 * Processes a list of content paths and indexes or de-indexes them as needed.
 * 
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
public class IndexPathsCommand implements IndexingCommand {

    private final DumAemSession session;
    private final List<String> paths;
    private final AemNodeNavigator nodeNavigator;
    private final DumAemJobService jobService;

    public IndexPathsCommand(DumAemSession session, List<String> paths,
            AemNodeNavigator nodeNavigator, DumAemJobService jobService) {
        this.session = session;
        this.paths = paths;
        this.nodeNavigator = nodeNavigator;
        this.jobService = jobService;
    }

    @Override
    public void execute() {
        log.info("Executing IndexPaths command for {} paths in source: {}",
                paths.size(), session.getSource());

        paths.stream()
                .filter(StringUtils::isNotBlank)
                .forEach(path -> indexPath(path, session.getEvent()));
    }

    /**
     * Indexes a single path, creating a de-index job if content is not found.
     */
    private void indexPath(String path, DumAemEvent event) {
        log.debug("Processing path: {}", path);

        if (event.equals(DumAemEvent.DEINDEXING)) {
            createDeIndexJob(path);
            return;
        }

        DumAemCommonsUtils.getInfinityJson(path, session.getConfiguration(), false)
                .ifPresentOrElse(
                        infinityJson -> nodeNavigator.navigateAndIndex(session, path, infinityJson),
                        () -> createDeIndexJob(path));
    }

    /**
     * Creates a de-index job for content that no longer exists.
     */
    private void createDeIndexJob(String path) {
        log.debug("Content not found, creating de-index job for path: {}", path);
        try {
            jobService.createDeIndexJobAndSendToConnectorQueue(session, path);
        } catch (Exception e) {
            log.error("Failed to create de-index job for path {}: {}", path, e.getMessage(), e);
        }
    }

    @Override
    public DumAemSession getSession() {
        return session;
    }

    @Override
    public String getDescription() {
        return "IndexPaths[source=%s, pathCount=%d]".formatted(session.getSource(), paths.size());
    }
}
