/*
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

package com.viglet.dumont.connector.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenanceEntry;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenanceSnapshot;
import com.viglet.dumont.connector.persistence.model.DumProvenanceFieldModel;
import com.viglet.dumont.connector.persistence.model.DumProvenanceRunModel;
import com.viglet.dumont.connector.persistence.repository.DumProvenanceRunRepository;

@ExtendWith(MockitoExtension.class)
class DumJpaProvenanceHistoryStoreTest {

    @Mock
    private DumProvenanceRunRepository repository;

    private DumJpaProvenanceHistoryStore store;

    @BeforeEach
    void setUp() {
        store = new DumJpaProvenanceHistoryStore(repository);
    }

    @Test
    void recordSnapshotPersistsRunAndReturnsSnapshot() {
        DumProvenanceSnapshot snapshot = store.recordSnapshot("s", 10,
                List.of(new DumFieldProvenanceEntry("title", "css", "h1", 0.9, 9, 10)));

        verify(repository).save(any(DumProvenanceRunModel.class));
        assertThat(snapshot.source()).isEqualTo("s");
        assertThat(snapshot.documentCount()).isEqualTo(10);
        assertThat(snapshot.fields()).singleElement().satisfies(f -> {
            assertThat(f.field()).isEqualTo("title");
            assertThat(f.strategy()).isEqualTo("css");
        });
    }

    @Test
    void historyMapsPersistedRunsToSnapshots() {
        DumProvenanceRunModel run = DumProvenanceRunModel.builder()
                .source("s").capturedAt(Instant.ofEpochMilli(1_700_000_000_000L)).documentCount(7)
                .fields(List.of(DumProvenanceFieldModel.builder().fieldName("title")
                        .strategy("css").selector("h1").confidence(0.8).present(6).total(7).build()))
                .build();
        when(repository.findAllBySourceOrderByCapturedAtDescIdDesc(eq("s"), any(Limit.class)))
                .thenReturn(List.of(run));

        List<DumProvenanceSnapshot> history = store.history("s");

        assertThat(history).singleElement().satisfies(s -> {
            assertThat(s.documentCount()).isEqualTo(7);
            assertThat(s.fields()).singleElement()
                    .satisfies(f -> assertThat(f.selector()).isEqualTo("h1"));
        });
    }
}
