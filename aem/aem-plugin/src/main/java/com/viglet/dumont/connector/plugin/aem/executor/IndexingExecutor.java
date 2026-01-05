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

package com.viglet.dumont.connector.plugin.aem.executor;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.plugin.aem.command.IndexingCommand;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;

import lombok.extern.slf4j.Slf4j;

/**
 * Executor for indexing commands with concurrency control.
 * Prevents duplicate executions for the same source using a thread-safe set.
 * 
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
@Component
public class IndexingExecutor {

    private final DumConnectorContext connectorContext;
    private final Set<String> runningSources = ConcurrentHashMap.newKeySet();

    public IndexingExecutor(DumConnectorContext connectorContext) {
        this.connectorContext = connectorContext;
    }

    /**
     * Executes a command that requires exclusive access to a source.
     * Only one instance of indexing can run per source at a time.
     * 
     * @param command the command to execute
     * @return true if executed, false if skipped due to running process
     */
    public boolean executeExclusive(IndexingCommand command) {
        String source = command.getSession().getSource();

        if (!runningSources.add(source)) {
            log.warn("Skipping {}. Source '{}' is already being processed.",
                    command.getDescription(), source);
            return false;
        }

        try {
            log.info("Starting exclusive execution: {}", command.getDescription());
            command.execute();
            finish(command.getSession(), false);
            return true;
        } catch (Exception e) {
            log.error("Error executing {}: {}", command.getDescription(), e.getMessage(), e);
            throw e;
        } finally {
            runningSources.remove(source);
            log.info("Completed exclusive execution: {}", command.getDescription());
        }
    }

    /**
     * Executes a standalone command without exclusive lock.
     * Multiple standalone commands can run concurrently for the same source.
     * 
     * @param command the command to execute
     */
    public void executeStandalone(IndexingCommand command) {
        try {
            log.info("Starting standalone execution: {}", command.getDescription());
            command.execute();
            finish(command.getSession(), true);
        } catch (Exception e) {
            log.error("Error executing {}: {}", command.getDescription(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Finishes the indexing process and notifies the connector context.
     */
    private void finish(DumAemSession session, boolean standalone) {
        connectorContext.finishIndexing(session, standalone);
        log.info("Finished indexing for source: {} (standalone={})",
                session.getSource(), standalone);
    }

    /**
     * Checks if a source is currently being processed with exclusive lock.
     * 
     * @param source the source name
     * @return true if the source is running
     */
    public boolean isRunning(String source) {
        return runningSources.contains(source);
    }

    /**
     * Gets the count of currently running sources.
     * 
     * @return the number of running sources
     */
    public int getRunningCount() {
        return runningSources.size();
    }
}
