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

import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobFingerprintEntry;

/** The cross-source fingerprint index (trabalharei <strong>TRA23</strong>). */
public interface DumJobFingerprintRepository extends JpaRepository<DumJobFingerprintEntry, String> {

    Optional<DumJobFingerprintEntry> findBySourceIdAndJobId(String sourceId, String jobId);

    /** Every row of {@code sourceId}, open or expired. */
    List<DumJobFingerprintEntry> findBySourceId(String sourceId);

    /** Every member, of any source, of the given groups. */
    List<DumJobFingerprintEntry> findByFingerprintIn(Collection<String> fingerprints);

    /** Drop every row of a source (used when the source itself is deleted). */
    @Transactional
    long deleteBySourceId(String sourceId);
}
