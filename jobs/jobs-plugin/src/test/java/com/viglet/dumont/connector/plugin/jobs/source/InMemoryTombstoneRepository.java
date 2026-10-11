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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobTombstone;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobTombstoneRepository;

/** A {@link DumJobTombstoneRepository} mock backed by a list, storing copies as a database would. */
public final class InMemoryTombstoneRepository {

    public final List<DumJobTombstone> rows = new ArrayList<>();
    public final DumJobTombstoneRepository repository = mock(DumJobTombstoneRepository.class);

    @SuppressWarnings("unchecked")
    public InMemoryTombstoneRepository() {
        lenient().when(repository.findByDocumentId(anyString())).thenAnswer(inv -> find(inv.getArgument(0))
                .map(InMemoryTombstoneRepository::copy));
        lenient().when(repository.findAllByOrderByQueuedAtAsc()).thenAnswer(inv -> rows.stream()
                .sorted(Comparator.comparing(DumJobTombstone::getQueuedAt))
                .map(InMemoryTombstoneRepository::copy).toList());
        lenient().when(repository.save(any(DumJobTombstone.class))).thenAnswer(inv -> {
            DumJobTombstone row = inv.getArgument(0);
            if (row.getId() == null) {
                if (find(row.getDocumentId()).isPresent()) {
                    throw new IllegalStateException("Duplicate document_id " + row.getDocumentId());
                }
                row.setId(UUID.randomUUID().toString());
            } else {
                rows.removeIf(r -> r.getId().equals(row.getId()));
            }
            rows.add(copy(row));
            return row;
        });
        lenient().doAnswer(inv -> {
            DumJobTombstone row = inv.getArgument(0);
            rows.removeIf(r -> r.getId().equals(row.getId()));
            return null;
        }).when(repository).delete(any(DumJobTombstone.class));
        lenient().when(repository.deleteByDocumentIdIn(any(Collection.class))).thenAnswer(inv -> {
            Collection<String> ids = inv.getArgument(0);
            int before = rows.size();
            rows.removeIf(r -> ids.contains(r.getDocumentId()));
            return (long) (before - rows.size());
        });
    }

    public Optional<DumJobTombstone> find(String documentId) {
        return rows.stream().filter(r -> r.getDocumentId().equals(documentId)).findFirst();
    }

    private static DumJobTombstone copy(DumJobTombstone row) {
        return row.toBuilder().build();
    }
}
