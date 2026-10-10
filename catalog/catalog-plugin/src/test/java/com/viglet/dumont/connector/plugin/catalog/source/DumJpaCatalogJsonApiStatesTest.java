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

package com.viglet.dumont.connector.plugin.catalog.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.viglet.dumont.connector.commons.spi.structured.jsonapi.DumJsonApiState;
import com.viglet.dumont.connector.commons.spi.structured.jsonapi.DumJsonApiState.Window;
import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogJsonApiCall;
import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogJsonApiSeen;
import com.viglet.dumont.connector.plugin.catalog.persistence.repository.DumCatalogJsonApiCallRepository;
import com.viglet.dumont.connector.plugin.catalog.persistence.repository.DumCatalogJsonApiSeenRepository;

/** The database-backed JSON API state of a catalog source (roadmap D92). */
class DumJpaCatalogJsonApiStatesTest {

    private static final Instant DAY = Instant.parse("2026-10-10T00:00:00Z");

    private final DumCatalogJsonApiCallRepository calls = mock(DumCatalogJsonApiCallRepository.class);
    private final DumCatalogJsonApiSeenRepository seen = mock(DumCatalogJsonApiSeenRepository.class);
    private final DumJsonApiState state = new DumJpaCatalogJsonApiStates(calls, seen).forSource("jobs");

    @Test
    void theFirstCallOfAWindowOpensItsRowAndLaterOnesCountOnIt() {
        when(calls.findBySourceNameAndWindowAndWindowStart("jobs", "DAY", DAY)).thenReturn(Optional.empty());
        state.addCall(Window.DAY, DAY);

        ArgumentCaptor<DumCatalogJsonApiCall> saved = ArgumentCaptor.forClass(DumCatalogJsonApiCall.class);
        verify(calls).save(saved.capture());
        DumCatalogJsonApiCall row = saved.getValue();
        assertThat(row.getSourceName()).isEqualTo("jobs");
        assertThat(row.getWindow()).isEqualTo("DAY");
        assertThat(row.getCalls()).isEqualTo(1);

        when(calls.findBySourceNameAndWindowAndWindowStart("jobs", "DAY", DAY)).thenReturn(Optional.of(row));
        state.addCall(Window.DAY, DAY);
        assertThat(state.calls(Window.DAY, DAY)).isEqualTo(2);
        assertThat(state.calls(Window.WEEK, DAY)).isZero();
    }

    @Test
    void markingSeenRefreshesKnownIdsAndAddsNewOnes() {
        DumCatalogJsonApiSeen known = seenRow("1", Instant.parse("2026-10-01T00:00:00Z"));
        when(seen.findBySourceName("jobs")).thenReturn(List.of(known));
        List<DumCatalogJsonApiSeen> saved = new ArrayList<>();
        when(seen.saveAll(any())).thenAnswer(inv -> {
            inv.<Iterable<DumCatalogJsonApiSeen>>getArgument(0).forEach(saved::add);
            return saved;
        });

        state.markSeen(List.of("1", "2"), DAY);

        assertThat(saved).extracting(DumCatalogJsonApiSeen::getObjectId).containsExactly("1", "2");
        assertThat(saved).allMatch(row -> row.getLastSeen().equals(DAY) && row.getSourceName().equals("jobs"));
        assertThat(saved.getFirst()).isSameAs(known);
    }

    @Test
    void expiringDeletesAndNamesTheStaleIds() {
        List<DumCatalogJsonApiSeen> stale = List.of(seenRow("7", Instant.parse("2026-09-01T00:00:00Z")));
        when(seen.findBySourceNameAndLastSeenBefore("jobs", DAY)).thenReturn(stale);

        assertThat(state.expire(DAY)).containsExactly("7");
        verify(seen).deleteAll(stale);
    }

    private static DumCatalogJsonApiSeen seenRow(String id, Instant at) {
        DumCatalogJsonApiSeen row = new DumCatalogJsonApiSeen();
        row.setSourceName("jobs");
        row.setObjectId(id);
        row.setLastSeen(at);
        return row;
    }
}
