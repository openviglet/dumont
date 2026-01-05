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

import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;

/**
 * Command interface for indexing operations.
 * Implements the Command Pattern for encapsulating indexing requests.
 * 
 * @author Alexandre Oliveira
 * @since 2026.1
 */
public interface IndexingCommand {

    /**
     * Executes the indexing command.
     */
    void execute();

    /**
     * Gets the session associated with this command.
     * 
     * @return the AEM session
     */
    DumAemSession getSession();

    /**
     * Gets a description of this command for logging purposes.
     * 
     * @return command description
     */
    String getDescription();
}
