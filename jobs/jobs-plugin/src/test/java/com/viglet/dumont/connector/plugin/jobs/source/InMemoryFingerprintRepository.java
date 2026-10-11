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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobFingerprintEntry;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobFingerprintRepository;

/**
 * A {@link DumJobFingerprintRepository} mock backed by a list, answering the queries the
 * deduplicator uses. Rows are stored as copies, as a database would, so a caller's later change to
 * an entity it holds is only seen once it is saved.
 */
public final class InMemoryFingerprintRepository {

    public final List<DumJobFingerprintEntry> rows = new ArrayList<>();
    public final DumJobFingerprintRepository repository = mock(DumJobFingerprintRepository.class);

    @SuppressWarnings("unchecked")
    public InMemoryFingerprintRepository() {
        lenient().when(repository.findBySourceId(anyString())).thenAnswer(inv -> rows.stream()
                .filter(r -> r.getSourceId().equals(inv.getArgument(0))).map(InMemoryFingerprintRepository::copy)
                .toList());
        lenient().when(repository.findBySourceIdAndJobId(anyString(), anyString())).thenAnswer(inv -> rows
                .stream().filter(r -> r.getSourceId().equals(inv.getArgument(0))
                        && r.getJobId().equals(inv.getArgument(1)))
                .findFirst().map(InMemoryFingerprintRepository::copy));
        lenient().when(repository.findByFingerprintIn(any(Collection.class))).thenAnswer(inv -> {
            Collection<String> fingerprints = inv.getArgument(0);
            return rows.stream().filter(r -> fingerprints.contains(r.getFingerprint()))
                    .map(InMemoryFingerprintRepository::copy).toList();
        });
        lenient().when(repository.saveAll(any(Iterable.class))).thenAnswer(inv -> {
            List<DumJobFingerprintEntry> saved = new ArrayList<>();
            for (DumJobFingerprintEntry entry : (Iterable<DumJobFingerprintEntry>) inv.getArgument(0)) {
                saved.add(save(entry));
            }
            return saved;
        });
        lenient().doAnswer(inv -> {
            for (DumJobFingerprintEntry entry : (Iterable<DumJobFingerprintEntry>) inv.getArgument(0)) {
                rows.removeIf(r -> r.getId().equals(entry.getId()));
            }
            return null;
        }).when(repository).deleteAll(any(Iterable.class));
        lenient().when(repository.deleteBySourceId(anyString())).thenAnswer(inv -> {
            int before = rows.size();
            rows.removeIf(r -> r.getSourceId().equals(inv.getArgument(0)));
            return (long) (before - rows.size());
        });
    }

    private DumJobFingerprintEntry save(DumJobFingerprintEntry entry) {
        if (entry.getId() == null) {
            boolean taken = rows.stream().anyMatch(r -> r.getSourceId().equals(entry.getSourceId())
                    && r.getJobId().equals(entry.getJobId()));
            if (taken) {
                throw new IllegalStateException("Duplicate (source_id, job_id): " + entry.getJobId());
            }
            entry.setId(UUID.randomUUID().toString());
        } else {
            rows.removeIf(r -> r.getId().equals(entry.getId()));
        }
        rows.add(copy(entry));
        return entry;
    }

    /** The rows of one group. */
    public List<DumJobFingerprintEntry> group(String fingerprint) {
        return rows.stream().filter(r -> r.getFingerprint().equals(fingerprint)).toList();
    }

    private static DumJobFingerprintEntry copy(DumJobFingerprintEntry entry) {
        return entry.toBuilder().build();
    }
}
