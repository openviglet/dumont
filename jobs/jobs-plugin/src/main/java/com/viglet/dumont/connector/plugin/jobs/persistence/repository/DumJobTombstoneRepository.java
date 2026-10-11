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

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobTombstone;

/** Tombstones not yet accepted by the job site (trabalharei <strong>TRA24</strong>). */
public interface DumJobTombstoneRepository extends JpaRepository<DumJobTombstone, String> {

    Optional<DumJobTombstone> findByDocumentId(String documentId);

    /** Every pending tombstone, oldest first. */
    List<DumJobTombstone> findAllByOrderByQueuedAtAsc();

    /** Drop the pending tombstones of documents that are published again. */
    @Transactional
    long deleteByDocumentIdIn(Collection<String> documentIds);
}
