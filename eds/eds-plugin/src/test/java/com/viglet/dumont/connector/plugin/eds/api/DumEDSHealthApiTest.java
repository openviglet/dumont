/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.plugin.eds.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.plugin.eds.DumEDSPlugin;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSCrawlState;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSIndex;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSSourceRepository;
import com.viglet.dumont.connector.plugin.eds.state.DumEDSCrawlStateService;

@ExtendWith(MockitoExtension.class)
class DumEDSHealthApiTest {

    @Mock
    private DumEDSSourceRepository sourceRepository;

    @Mock
    private DumEDSCrawlStateService crawlStateService;

    @Test
    void reportsStatusUpAndPerSourceRollup() {
        DumEDSSource source = source("src-1", "Example PT-BR", "https://example.invalid");
        source.getIndexes().add(new DumEDSIndex("/query-index.json", source));

        Instant lastSeen = Instant.parse("2026-05-28T10:00:00Z");
        Instant lastIndexed = Instant.parse("2026-05-28T09:55:00Z");

        DumEDSCrawlState older = new DumEDSCrawlState();
        older.setLastSeenAt(lastSeen.minusSeconds(3600));
        older.setLastIndexedAt(lastIndexed.minusSeconds(3600));

        DumEDSCrawlState newer = new DumEDSCrawlState();
        newer.setLastSeenAt(lastSeen);
        newer.setLastIndexedAt(lastIndexed);

        when(sourceRepository.findAll()).thenReturn(List.of(source));
        when(crawlStateService.findAll(any())).thenReturn(List.of(older, newer));

        DumEDSHealthApi api = new DumEDSHealthApi(sourceRepository, crawlStateService);
        DumEDSHealthApi.Health health = api.health();

        assertThat(health.status()).isEqualTo(DumEDSHealthApi.STATUS_UP);
        assertThat(health.providerName()).isEqualTo(DumEDSPlugin.PROVIDER_NAME);
        assertThat(health.sourceCount()).isEqualTo(1);
        assertThat(health.sources()).singleElement().satisfies(s -> {
            assertThat(s.id()).isEqualTo("src-1");
            assertThat(s.title()).isEqualTo("Example PT-BR");
            assertThat(s.baseUrl()).isEqualTo("https://example.invalid");
            assertThat(s.indexCount()).isEqualTo(1);
            assertThat(s.pagesIndexed()).isEqualTo(2);
            assertThat(s.lastSeenAt()).isEqualTo(lastSeen);
            assertThat(s.lastIndexedAt()).isEqualTo(lastIndexed);
        });
    }

    @Test
    void returnsEmptySourcesListWhenNothingConfigured() {
        when(sourceRepository.findAll()).thenReturn(List.of());

        DumEDSHealthApi api = new DumEDSHealthApi(sourceRepository, crawlStateService);

        DumEDSHealthApi.Health health = api.health();
        assertThat(health.status()).isEqualTo(DumEDSHealthApi.STATUS_UP);
        assertThat(health.sourceCount()).isZero();
        assertThat(health.sources()).isEmpty();
    }

    @Test
    void handlesSourceWithNoCrawlStateYet() {
        DumEDSSource source = source("src-1", "Fresh", "https://example.invalid");
        when(sourceRepository.findAll()).thenReturn(List.of(source));
        when(crawlStateService.findAll(any())).thenReturn(List.of());

        DumEDSHealthApi.Health health = new DumEDSHealthApi(sourceRepository, crawlStateService)
                .health();

        assertThat(health.sources()).singleElement().satisfies(s -> {
            assertThat(s.pagesIndexed()).isZero();
            assertThat(s.lastSeenAt()).isNull();
            assertThat(s.lastIndexedAt()).isNull();
        });
    }

    private static DumEDSSource source(String id, String title, String baseUrl) {
        DumEDSSource source = new DumEDSSource();
        source.setId(id);
        source.setTitle(title);
        source.setBaseUrl(baseUrl);
        return source;
    }
}
