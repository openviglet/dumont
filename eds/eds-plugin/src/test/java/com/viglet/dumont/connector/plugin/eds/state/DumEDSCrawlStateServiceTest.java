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

package com.viglet.dumont.connector.plugin.eds.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSCrawlState;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSCrawlStateRepository;

class DumEDSCrawlStateServiceTest {

    private DumEDSCrawlStateRepository repository;
    private DumEDSCrawlStateService service;
    private final DumEDSSource source = DumEDSSource.builder().id("s1").build();

    @BeforeEach
    void setUp() {
        repository = mock(DumEDSCrawlStateRepository.class);
        service = new DumEDSCrawlStateService(repository);
    }

    private static DumEDSCrawlState state(String path) {
        DumEDSCrawlState state = new DumEDSCrawlState();
        state.setPath(path);
        return state;
    }

    // --- isUnchanged (pure logic) ---

    @Test
    void isUnchangedFalseWhenPriorIsNull() {
        assertThat(service.isUnchanged(null, "2026-05-01T00:00:00Z")).isFalse();
    }

    @Test
    void isUnchangedFalseWhenEitherSideBlank() {
        DumEDSCrawlState prior = new DumEDSCrawlState();
        prior.setLastModified(null);
        assertThat(service.isUnchanged(prior, "2026-05-01")).isFalse();

        prior.setLastModified("2026-05-01");
        assertThat(service.isUnchanged(prior, null)).isFalse();
        assertThat(service.isUnchanged(prior, "")).isFalse();
        assertThat(service.isUnchanged(prior, "   ")).isFalse();
    }

    @Test
    void isUnchangedTrueOnExactMatch() {
        DumEDSCrawlState prior = new DumEDSCrawlState();
        prior.setLastModified("2026-05-01T12:00:00Z");
        assertThat(service.isUnchanged(prior, "2026-05-01T12:00:00Z")).isTrue();
    }

    @Test
    void isUnchangedFalseOnDifferentStamp() {
        DumEDSCrawlState prior = new DumEDSCrawlState();
        prior.setLastModified("2026-05-01T12:00:00Z");
        assertThat(service.isUnchanged(prior, "2026-05-02T12:00:00Z")).isFalse();
    }

    // --- repository-backed methods ---

    @Test
    void loadByPathKeysStatesByPath() {
        when(repository.findByDumEDSSource(source)).thenReturn(List.of(state("/a"), state("/b")));
        Map<String, DumEDSCrawlState> map = service.loadByPath(source);
        assertThat(map).containsKeys("/a", "/b");
    }

    @Test
    void upsertCreatesNewStateWhenAbsent() {
        when(repository.findByDumEDSSourceAndPath(source, "/a")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DumEDSCrawlState result = service.upsert(source, "/a", "2024-01-01", "chk", true);

        assertThat(result.getChecksum()).isEqualTo("chk");
        assertThat(result.getLastIndexedAt()).isNotNull();
        verify(repository).save(any(DumEDSCrawlState.class));
    }

    @Test
    void touchUpdatesLastSeenAndSaves() {
        DumEDSCrawlState prior = state("/a");
        when(repository.save(prior)).thenReturn(prior);
        DumEDSCrawlState result = service.touch(prior);
        assertThat(result.getLastSeenAt()).isNotNull();
        verify(repository).save(prior);
    }

    @Test
    void deleteAndFindAllDelegate() {
        when(repository.deleteByDumEDSSourceAndPath(source, "/a")).thenReturn(1);
        assertThat(service.delete(source, "/a")).isEqualTo(1);

        when(repository.deleteByDumEDSSource(source)).thenReturn(3);
        assertThat(service.deleteAll(source)).isEqualTo(3);

        when(repository.findByDumEDSSource(source)).thenReturn(List.of(state("/a")));
        assertThat(service.findAll(source)).hasSize(1);
    }
}
