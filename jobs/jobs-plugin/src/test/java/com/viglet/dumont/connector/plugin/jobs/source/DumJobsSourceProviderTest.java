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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.plugin.jobs.DumJobsPlugin;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSourceRepository;

class DumJobsSourceProviderTest {

    private final DumJobSourceRepository repository = mock(DumJobSourceRepository.class);
    private final InMemorySeenRepository seen = new InMemorySeenRepository();

    private static DumJobSourceAdapter adapter(String id) {
        return new DumJobSourceAdapter() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public List<Map<String, Object>> fetch(DumJobFetchContext context) {
                return List.of();
            }
        };
    }

    private DumJobsSourceProvider provider(DumJobSourceAdapter... adapters) {
        return new DumJobsSourceProvider(repository, seen.repository, List.of(adapters),
                new MutableClock(Instant.parse("2026-10-10T12:00:00Z")));
    }

    private static DumJobSource source(String name, String type, boolean enabled) {
        return DumJobSource.builder().id(name).name(name).sourceType(type).enabled(enabled).build();
    }

    @Test
    void withNoAdaptersEverySourceIsSkippedWithoutFailing() {
        DumJobSource adzuna = source("adzuna-br", "adzuna", true);
        when(repository.findByEnabledTrue()).thenReturn(List.of(adzuna));
        when(repository.findByName("adzuna-br")).thenReturn(Optional.of(adzuna));

        DumJobsSourceProvider provider = provider();

        assertThat(provider.sourceNames()).isEmpty();
        assertThat(provider.resolve("adzuna-br")).isEmpty();
    }

    @Test
    void unknownTypeIsSkippedAndKnownTypeResolves() {
        DumJobSource adzuna = source("adzuna-br", "Adzuna", true);
        DumJobSource mystery = source("mystery", "monster", true);
        DumJobSource noType = source("untyped", null, true);
        when(repository.findByEnabledTrue()).thenReturn(List.of(adzuna, mystery, noType));
        when(repository.findByName("adzuna-br")).thenReturn(Optional.of(adzuna));
        when(repository.findByName("mystery")).thenReturn(Optional.of(mystery));

        DumJobsSourceProvider provider = provider(adapter("adzuna"));

        assertThat(provider.sourceNames()).containsExactly("adzuna-br");
        assertThat(provider.resolve("adzuna-br")).hasValueSatisfying(s -> {
            assertThat(s).isInstanceOf(DumJobsStructuredSource.class);
            assertThat(s.getSourceName()).isEqualTo("adzuna-br");
        });
        assertThat(provider.resolve("mystery")).isEmpty();
        assertThat(provider.getProviderName()).isEqualTo(DumJobsPlugin.PROVIDER_NAME);
    }

    @Test
    void disabledSourceDoesNotResolve() {
        DumJobSource off = source("jooble-br", "jooble", false);
        when(repository.findByName("jooble-br")).thenReturn(Optional.of(off));
        when(repository.findByName("absent")).thenReturn(Optional.empty());

        DumJobsSourceProvider provider = provider(adapter("jooble"));

        assertThat(provider.resolve("jooble-br")).isEmpty();
        assertThat(provider.resolve("absent")).isEmpty();
    }
}
