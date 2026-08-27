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

    List<DumConnectorIndexing> getIndexingItem(String objectId, String source, String provider);

    List<String> getObjectIdByDependency(String source, String provider,
            List<String> dependenciesObjectIdList);
}
