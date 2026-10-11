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

    /**
     * Keeps ids alive in the current run without re-sending them (roadmap <strong>D104</strong>):
     * their tracking rows take the session's transaction, so a sweeping finish does not deindex
     * them. For a source that knows an item is unchanged and still present but did not fetch it
     * again. A no-op by default.
     *
     * @return how many tracked ids were kept
     */
    default int keepAlive(DumConnectorSession session, Collection<String> objectIds) {
        return 0;
    }

    /**
     * Records how a run ended (roadmap <strong>D96</strong>), before its
     * {@link #finishIndexing finish}: whether it stopped short of the whole source, and the
     * notes that say why, so the run record shows a budget stop or a rate limit instead of a
     * successful smaller run. A no-op by default.
     *
     * @param session the run
     * @param partial whether the run ended before reaching everything the source holds
     * @param notes   short human-readable notes, possibly empty
     */
    default void recordRunOutcome(DumConnectorSession session, boolean partial, List<String> notes) {
        // Nowhere to record it.
    }

    /**
     * {@link #recordRunOutcome(DumConnectorSession, boolean, List)} with the run's warnings
     * (roadmap <strong>D130</strong>): what the source says an operator must act on, which marks
     * the run as warned whether or not it is partial. A context that keeps no warnings records the
     * rest, so this defaults to dropping them.
     *
     * @param warnings short human-readable warnings, possibly empty
     */
    default void recordRunOutcome(DumConnectorSession session, boolean partial, List<String> notes,
            List<String> warnings) {
        recordRunOutcome(session, partial, notes);
    }

    List<DumConnectorIndexing> getIndexingItem(String objectId, String source, String provider);

    List<String> getObjectIdByDependency(String source, String provider,
            List<String> dependenciesObjectIdList);
}
