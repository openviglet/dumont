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

import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageSnapshot;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumFieldCoverage;
import com.viglet.dumont.connector.persistence.model.DumCoverageFieldModel;
import com.viglet.dumont.connector.persistence.model.DumCoverageRunModel;
import com.viglet.dumont.connector.persistence.repository.DumCoverageRunRepository;

@ExtendWith(MockitoExtension.class)
class DumJpaCoverageHistoryStoreTest {

    @Mock
    private DumCoverageRunRepository repository;

    private DumJpaCoverageHistoryStore store;

    @BeforeEach
    void setUp() {
        store = new DumJpaCoverageHistoryStore(repository);
    }

    @Test
    void recordSnapshotPersistsRunAndReturnsSnapshot() {
        DumCoverageSnapshot snapshot = store.recordSnapshot("s", 100,
                List.of(new DumFieldCoverage("title", "STRING", true, 90, 100, 90)));

        verify(repository).save(any(DumCoverageRunModel.class));
        assertThat(snapshot.source()).isEqualTo("s");
        assertThat(snapshot.documentCount()).isEqualTo(100);
        assertThat(snapshot.fields()).singleElement()
                .satisfies(f -> assertThat(f.field()).isEqualTo("title"));
    }

    @Test
    void historyMapsPersistedRunsToSnapshots() {
        DumCoverageRunModel run = DumCoverageRunModel.builder()
                .source("s").capturedAt(Instant.ofEpochMilli(1_700_000_000_000L)).documentCount(42)
                .fields(List.of(DumCoverageFieldModel.builder().fieldName("title")
                        .fieldType("STRING").mandatory(true).present(40).total(42).percent(95)
                        .build()))
                .build();
        when(repository.findAllBySourceOrderByCapturedAtDescIdDesc(eq("s"), any(Limit.class)))
                .thenReturn(List.of(run));

        List<DumCoverageSnapshot> history = store.history("s");

        assertThat(history).singleElement().satisfies(s -> {
            assertThat(s.documentCount()).isEqualTo(42);
            assertThat(s.fields()).singleElement()
                    .satisfies(f -> assertThat(f.percent()).isEqualTo(95));
        });
    }
}
