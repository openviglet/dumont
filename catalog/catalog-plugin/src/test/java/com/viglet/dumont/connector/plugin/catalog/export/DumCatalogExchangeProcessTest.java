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

package com.viglet.dumont.connector.plugin.catalog.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogSource;
import com.viglet.dumont.connector.plugin.catalog.persistence.repository.DumCatalogSourceRepository;

@ExtendWith(MockitoExtension.class)
class DumCatalogExchangeProcessTest {

    @Mock
    private DumCatalogSourceRepository sourceRepository;

    private DumCatalogExchangeProcess process;

    @BeforeEach
    void setUp() {
        process = new DumCatalogExchangeProcess(sourceRepository);
    }

    @Test
    void exportJsonSerializesEveryConfiguredSource() {
        DumCatalogSource source = DumCatalogSource.builder()
                .name("UFRJ").description("Federal").catalogUrl("https://x.invalid/catalog.json")
                .authHeaderName("X-Key").authHeaderValue("secret").locale("pt_BR")
                .turSNSites(new java.util.HashSet<>(List.of("Catalog"))).build();
        when(sourceRepository.findAll()).thenReturn(List.of(source));

        String json = process.exportJson();

        assertThat(json).contains("UFRJ").contains("https://x.invalid/catalog.json")
                .contains("X-Key").contains("Catalog");
    }

    @Test
    void importFromStringUpsertsNewSourceByName() {
        when(sourceRepository.findByName("UFRJ")).thenReturn(Optional.empty());
        ArgumentCaptor<DumCatalogSource> saved = ArgumentCaptor.forClass(DumCatalogSource.class);

        process.importFromString("""
                [{"name":"UFRJ","description":"Federal","catalogUrl":"https://x.invalid/c.json",
                  "locale":"pt_BR","turSNSites":["Catalog","News"]}]
                """);

        verify(sourceRepository).save(saved.capture());
        DumCatalogSource value = saved.getValue();
        assertThat(value.getName()).isEqualTo("UFRJ");
        assertThat(value.getCatalogUrl()).isEqualTo("https://x.invalid/c.json");
        assertThat(value.getTurSNSites()).containsExactlyInAnyOrder("Catalog", "News");
    }

    @Test
    void importFromStringUpdatesExistingSource() {
        DumCatalogSource existing = DumCatalogSource.builder().name("UFRJ")
                .description("old").build();
        when(sourceRepository.findByName("UFRJ")).thenReturn(Optional.of(existing));

        process.importFromString(
                "[{\"name\":\"UFRJ\",\"catalogUrl\":\"https://x.invalid/c.json\"}]");

        verify(sourceRepository).save(existing);
        assertThat(existing.getCatalogUrl()).isEqualTo("https://x.invalid/c.json");
    }

    @Test
    void importIgnoresNonArrayPayload() {
        process.importFromString("{\"name\":\"x\"}");
        process.importFromString("");
        process.importFromString(null);

        verify(sourceRepository, never()).save(any());
    }

    @Test
    void importSkipsEntriesWithoutCatalogUrlOrName() {
        process.importFromString("[{\"name\":\"NoUrl\"},{\"catalogUrl\":\"https://x/c.json\"}]");

        verify(sourceRepository, never()).save(any());
    }

    @Test
    void importFromFileReadsAndUpserts(@TempDir Path tempDir) throws Exception {
        when(sourceRepository.findByName("UFRJ")).thenReturn(Optional.empty());
        Path file = tempDir.resolve("catalog.json");
        Files.writeString(file,
                "[{\"name\":\"UFRJ\",\"catalogUrl\":\"https://x.invalid/c.json\"}]",
                StandardCharsets.UTF_8);

        process.importFromFile(file.toFile());

        verify(sourceRepository).save(any(DumCatalogSource.class));
    }

    @Test
    void importFromMissingFileLogsErrorWithoutThrowing(@TempDir Path tempDir) {
        process.importFromFile(tempDir.resolve("nope.json").toFile());

        verify(sourceRepository, never()).save(any());
    }
}
