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

import com.viglet.dumont.connector.aem.commons.DumAemCommonsUtils;
import com.viglet.dumont.connector.plugin.aem.command.IndexingCommand;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.navigator.AemNodeNavigator;

import lombok.extern.slf4j.Slf4j;

/**
 * Command for indexing all content from a source.
 * Traverses the entire content tree starting from the configured root path.
 * 
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
public class IndexAllCommand implements IndexingCommand {

    private final DumAemSession session;
    private final AemNodeNavigator nodeNavigator;

    public IndexAllCommand(DumAemSession session, AemNodeNavigator nodeNavigator) {
        this.session = session;
        this.nodeNavigator = nodeNavigator;
    }

    @Override
    public void execute() {
        log.info("Executing IndexAll command for source: {}", session.getSource());

        if (!DumAemCommonsUtils.usingContentTypeParameter(session.getConfiguration())) {
            log.warn("Content type parameter not configured for source: {}", session.getSource());
            return;
        }

        String rootPath = session.getConfiguration().getRootPath();
        DumAemCommonsUtils.getInfinityJson(rootPath, session.getConfiguration(), false)
                .ifPresent(infinityJson -> nodeNavigator.navigateAndIndex(session, rootPath, infinityJson));
    }

    @Override
    public DumAemSession getSession() {
        return session;
    }

    @Override
    public String getDescription() {
        return "IndexAll[source=%s]".formatted(session.getSource());
    }
}
