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

package com.viglet.dumont.connector.plugin.webcrawler.state;

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

import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCCrawlState;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCSource;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCCrawlStateRepository;

class DumWCCrawlStateServiceTest {

    private DumWCCrawlStateRepository repository;
    private DumWCCrawlStateService service;
    private final DumWCSource source = DumWCSource.builder().id("s1").build();

    @BeforeEach
    void setUp() {
        repository = mock(DumWCCrawlStateRepository.class);
        service = new DumWCCrawlStateService(repository);
    }

    private static DumWCCrawlState state(String url) {
        DumWCCrawlState state = new DumWCCrawlState();
        state.setUrl(url);
        return state;
    }

    @Test
    void loadByUrlKeysStatesByUrl() {
        when(repository.findByDumWCSource(source))
                .thenReturn(List.of(state("https://x/a"), state("https://x/b")));

        Map<String, DumWCCrawlState> map = service.loadByUrl(source);

        assertThat(map).containsKeys("https://x/a", "https://x/b");
    }

    @Test
    void isSitemapUnchangedHandlesNullAndMatches() {
        assertThat(service.isSitemapUnchanged(null, "2024-01-01")).isFalse();

        DumWCCrawlState prior = state("https://x/a");
        prior.setSitemapLastmod("2024-01-01");
        assertThat(service.isSitemapUnchanged(prior, "2024-01-01")).isTrue();
        assertThat(service.isSitemapUnchanged(prior, "2024-02-02")).isFalse();
    }

    @Test
    void upsertCreatesNewStateWhenAbsent() {
        when(repository.findByDumWCSourceAndUrl(source, "https://x/a")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DumWCCrawlState result = service.upsert(source, "https://x/a", "etag1", "Mon",
                "2024-01-01", "chk", true);

        assertThat(result.getEtag()).isEqualTo("etag1");
        assertThat(result.getChecksum()).isEqualTo("chk");
        assertThat(result.getLastIndexedAt()).isNotNull();
        verify(repository).save(any(DumWCCrawlState.class));
    }

    @Test
    void upsertUpdatesExistingStateSelectively() {
        DumWCCrawlState existing = state("https://x/a");
        existing.setEtag("old");
        when(repository.findByDumWCSourceAndUrl(source, "https://x/a"))
                .thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // blank fields must not overwrite existing values
        DumWCCrawlState result = service.upsert(source, "https://x/a", "", null, "", null, false);

        assertThat(result.getEtag()).isEqualTo("old");
        assertThat(result.getLastSeenAt()).isNotNull();
    }
}
