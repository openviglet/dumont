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

package com.viglet.dumont.connector.commons;

import java.util.List;
import java.util.Locale;

/**
 * Told of each document the connector deindexes for a {@link DumConnectorSession} (roadmap
 * <strong>D113</strong>): the run-end sweep's removals, an incremental run's expired ids, and any
 * other delete queued under the session. Set with {@link DumConnectorSession#onDeindex}.
 *
 * <p>
 * It is called once the delete is queued, on the thread finishing the run and while the source's
 * indexing lock is held, so it should only record what it is told. A listener that throws does not
 * stop the delete or the rest of the sweep.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
@FunctionalInterface
public interface DumDeindexListener {

    /**
     * @param objectId the document id that left the index
     * @param sites    the sites the delete was sent to
     * @param locale   the document's locale
     */
    void deindexed(String objectId, List<String> sites, Locale locale);
}
