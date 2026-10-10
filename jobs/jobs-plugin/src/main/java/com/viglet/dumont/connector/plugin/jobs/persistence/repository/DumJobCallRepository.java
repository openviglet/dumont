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

package com.viglet.dumont.connector.plugin.jobs.persistence.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobCall;

public interface DumJobCallRepository extends JpaRepository<DumJobCall, String> {

    /** The calls {@code sourceId} made after {@code since} — what seeds the next run's budget. */
    List<DumJobCall> findBySourceIdAndCalledAtAfter(String sourceId, Instant since);

    /** Prune the calls of {@code sourceId} made at or before {@code before}. */
    @Transactional
    long deleteBySourceIdAndCalledAtLessThanEqual(String sourceId, Instant before);

    /** Drop the whole call history of a source (used when the source itself is deleted). */
    @Transactional
    long deleteBySourceId(String sourceId);
}
