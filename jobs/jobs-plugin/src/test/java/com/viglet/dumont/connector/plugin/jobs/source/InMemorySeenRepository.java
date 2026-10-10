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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSeen;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSeenRepository;

/** A {@link DumJobSeenRepository} mock backed by a list, answering the queries the source uses. */
final class InMemorySeenRepository {

    final List<DumJobSeen> rows = new ArrayList<>();
    final DumJobSeenRepository repository = mock(DumJobSeenRepository.class);

    InMemorySeenRepository() {
        lenient().when(repository.findBySourceIdAndJobId(anyString(), anyString()))
                .thenAnswer(inv -> find(inv.getArgument(0), inv.getArgument(1)));
        lenient().when(repository.save(any(DumJobSeen.class))).thenAnswer(inv -> {
            DumJobSeen seen = inv.getArgument(0);
            if (seen.getId() == null) {
                seen.setId(UUID.randomUUID().toString());
                rows.add(seen);
            }
            return seen;
        });
        lenient().when(repository.findBySourceIdAndExpiresAtAfter(anyString(), any(Instant.class)))
                .thenAnswer(inv -> rows.stream()
                        .filter(r -> r.getSourceId().equals(inv.getArgument(0))
                                && r.getExpiresAt().isAfter(inv.getArgument(1)))
                        .toList());
        lenient().when(repository.deleteBySourceIdAndExpiresAtLessThanEqual(anyString(),
                any(Instant.class))).thenAnswer(inv -> {
                    Instant now = inv.getArgument(1);
                    List<DumJobSeen> gone = rows.stream()
                            .filter(r -> r.getSourceId().equals(inv.getArgument(0))
                                    && !r.getExpiresAt().isAfter(now))
                            .toList();
                    rows.removeAll(gone);
                    return (long) gone.size();
                });
    }

    /** Seed a row as an earlier run would have left it. */
    void seed(String sourceId, String jobId, Instant expiresAt, String payload) {
        rows.add(DumJobSeen.builder().id(UUID.randomUUID().toString()).sourceId(sourceId)
                .jobId(jobId).expiresAt(expiresAt).lastSeenAt(expiresAt.minusSeconds(86400))
                .payload(payload).build());
    }

    Optional<DumJobSeen> find(String sourceId, String jobId) {
        return rows.stream()
                .filter(r -> r.getSourceId().equals(sourceId) && r.getJobId().equals(jobId))
                .findFirst();
    }
}
