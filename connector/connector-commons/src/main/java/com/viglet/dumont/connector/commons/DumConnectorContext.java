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

package com.viglet.dumont.connector.commons;

import java.util.Collection;
import java.util.List;

import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumIndexingSchema;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;

public interface DumConnectorContext {
    boolean addJobItem(DumJobItemWithSession dumJobItemWithSession);

    void finishIndexing(DumConnectorSession session, boolean standalone);

    /**
     * Declares a source's field schema to the configured search backend before its documents
     * (roadmap <strong>D63</strong>). A no-op by default, so a plugin or test context that predates
     * provisioning is unchanged.
     *
     * @param schema the site, locale and declared fields
     */
    default void provisionSchema(DumIndexingSchema schema) {
        // No backend to declare to.
    }

    /**
     * Deindexes an explicitly named set of ids (roadmap <strong>D68</strong>), so a deliberate
     * repair — an operator applying an audit's orphan report — goes through the same delete job and
     * the same queue as the deindex a finished run performs, instead of deleting behind the
     * connector's back and leaving the ledger describing documents the index no longer holds.
     *
     * <p>
     * A no-op by default: a context that never owned a queue has nothing to send.
     *
     * @param session   the source, provider and locale to stamp on the delete jobs
     * @param objectIds the ids to remove
     * @param sites     the sites to remove them from
     * @return how many delete jobs were queued
     */
    default int deIndexById(DumConnectorSession session, Collection<String> objectIds,
            List<String> sites) {
        return 0;
    }

    List<DumConnectorIndexing> getIndexingItem(String objectId, String source, String provider);

    List<String> getObjectIdByDependency(String source, String provider,
            List<String> dependenciesObjectIdList);
}
