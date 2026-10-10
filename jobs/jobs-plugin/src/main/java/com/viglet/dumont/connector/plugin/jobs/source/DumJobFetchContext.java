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

package com.viglet.dumont.connector.plugin.jobs.source;

import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;

/**
 * What an adapter gets for one fetch (trabalharei <strong>TRA13</strong>): the source configuration
 * (credentials, API URL, locale) and the call budget it must ask before every partner request.
 *
 * @param source the configured source being fetched
 * @param budget the call budget for this run; {@link DumJobCallBudget#tryAcquire()} before each call
 */
public record DumJobFetchContext(DumJobSource source, DumJobCallBudget budget) {

    public DumJobFetchContext {
        if (source == null) {
            throw new IllegalArgumentException("A fetch context needs a source");
        }
        if (budget == null) {
            throw new IllegalArgumentException("A fetch context needs a call budget");
        }
    }
}
