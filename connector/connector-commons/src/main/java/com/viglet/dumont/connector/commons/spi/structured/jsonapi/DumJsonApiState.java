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

package com.viglet.dumont.connector.commons.spi.structured.jsonapi;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.List;

/**
 * What one JSON API source remembers between runs (roadmap <strong>D92</strong>): the calls spent
 * in each budget window, and when each record was last seen. Both have to outlive a run — a daily
 * budget spent across three runs is one budget, and an id an incremental run did not reach is only
 * gone once it has been missing for longer than the expiry.
 *
 * <p>
 * One instance serves one source. {@link DumInMemoryJsonApiState} is the default and forgets on
 * restart; a plugin that owns a database passes a persistent one.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface DumJsonApiState {

    /** The calls already made in the window that starts at {@code windowStart}. */
    long calls(Window window, Instant windowStart);

    /** Counts one call against the window that starts at {@code windowStart}. */
    void addCall(Window window, Instant windowStart);

    /** Records that these ids were present at {@code at}. */
    void markSeen(Collection<String> ids, Instant at);

    /** Forgets, and returns, every id last seen before {@code cutoff}. */
    List<String> expire(Instant cutoff);

    /** A calendar budget window, in UTC. */
    enum Window {
        DAY, WEEK, MONTH;

        /** The start of the window holding {@code at}: midnight, Monday, or the first of the month. */
        public Instant start(Instant at) {
            LocalDate date = at.atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate start = switch (this) {
                case DAY -> date;
                case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                case MONTH -> date.withDayOfMonth(1);
            };
            return start.atStartOfDay(ZoneOffset.UTC).toInstant();
        }
    }
}
