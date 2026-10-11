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
import java.util.UUID;

import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobCall;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobCallRepository;

/** A {@link DumJobCallRepository} mock backed by a list, answering the queries the source uses. */
public final class InMemoryCallRepository {

    public final List<DumJobCall> rows = new ArrayList<>();
    public final DumJobCallRepository repository = mock(DumJobCallRepository.class);

    public InMemoryCallRepository() {
        lenient().when(repository.save(any(DumJobCall.class))).thenAnswer(inv -> {
            DumJobCall call = inv.getArgument(0);
            if (call.getId() == null) {
                call.setId(UUID.randomUUID().toString());
                rows.add(call);
            }
            return call;
        });
        lenient().when(repository.findBySourceIdAndCalledAtAfter(anyString(), any(Instant.class)))
                .thenAnswer(inv -> rows.stream()
                        .filter(r -> r.getSourceId().equals(inv.getArgument(0))
                                && r.getCalledAt().isAfter(inv.getArgument(1)))
                        .toList());
        lenient().when(repository.deleteBySourceIdAndCalledAtLessThanEqual(anyString(),
                any(Instant.class))).thenAnswer(inv -> {
                    Instant before = inv.getArgument(1);
                    List<DumJobCall> gone = rows.stream()
                            .filter(r -> r.getSourceId().equals(inv.getArgument(0))
                                    && !r.getCalledAt().isAfter(before))
                            .toList();
                    rows.removeAll(gone);
                    return (long) gone.size();
                });
    }

    /** Seed a call as an earlier run would have left it. */
    public void seed(String sourceId, Instant calledAt) {
        rows.add(DumJobCall.builder().id(UUID.randomUUID().toString()).sourceId(sourceId)
                .calledAt(calledAt).build());
    }

    /** The calls recorded for {@code sourceId}. */
    public List<Instant> calls(String sourceId) {
        return rows.stream().filter(r -> r.getSourceId().equals(sourceId))
                .map(DumJobCall::getCalledAt).sorted().toList();
    }
}
