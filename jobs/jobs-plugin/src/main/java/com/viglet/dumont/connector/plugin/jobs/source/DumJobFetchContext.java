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

import java.time.Instant;
import java.util.function.Function;
import java.util.function.Predicate;

import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;

/**
 * What an adapter gets for one fetch (trabalharei <strong>TRA13</strong>): the source configuration
 * (credentials, API URL, locale, settings), the call budget it must ask before every partner request
 * and, since <strong>TRA15</strong>, which posting ids earlier runs already fetched, so a newest-first
 * adapter can stop paging once it reaches postings it has. Since <strong>TRA18</strong> it also
 * answers when a posting was first fetched, for a partner that sends no date of its own (BNE).
 *
 * @param source    the configured source being fetched
 * @param budget    the call budget for this run; {@link DumJobCallBudget#tryAcquire()} before each call
 * @param seen      whether a document id (e.g. {@code adzuna-4512}) was fetched by an earlier run of
 *                  this source; it never reports ids this run fetched
 * @param firstSeen when a document id was first fetched by this source: an earlier run's time, or this
 *                  run's when the id is new; {@code null} when the caller keeps no history
 */
public record DumJobFetchContext(DumJobSource source, DumJobCallBudget budget, Predicate<String> seen,
        Function<String, Instant> firstSeen) {

    public DumJobFetchContext {
        if (source == null) {
            throw new IllegalArgumentException("A fetch context needs a source");
        }
        if (budget == null) {
            throw new IllegalArgumentException("A fetch context needs a call budget");
        }
        if (seen == null) {
            seen = id -> false;
        }
        if (firstSeen == null) {
            firstSeen = id -> null;
        }
    }

    /** A context with no seen history: every id reads as new. */
    public DumJobFetchContext(DumJobSource source, DumJobCallBudget budget) {
        this(source, budget, null, null);
    }

    /** A context that knows which ids earlier runs fetched, but not when. */
    public DumJobFetchContext(DumJobSource source, DumJobCallBudget budget, Predicate<String> seen) {
        this(source, budget, seen, null);
    }

    /** Whether {@code id} was fetched by an earlier run. */
    public boolean wasSeen(String id) {
        return id != null && seen.test(id);
    }

    /**
     * When {@code id} was first fetched — an earlier run's time, or this run's for a new id; {@code null}
     * when the context keeps no history (the adapter then falls back to its own run time).
     */
    public Instant firstSeenAt(String id) {
        return id == null ? null : firstSeen.apply(id);
    }
}
