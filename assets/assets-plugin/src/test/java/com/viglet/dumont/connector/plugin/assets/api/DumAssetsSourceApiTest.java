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

package com.viglet.dumont.connector.plugin.assets.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.viglet.dumont.connector.plugin.assets.DumAssetsPluginProcess;
import com.viglet.dumont.connector.plugin.assets.api.dto.DumAssetsSourceRequest;
import com.viglet.dumont.connector.plugin.assets.export.DumAssetsExchangeProcess;
import com.viglet.dumont.connector.plugin.assets.persistence.model.DumAssetsSource;
import com.viglet.dumont.connector.plugin.assets.persistence.repository.DumAssetsSourceRepository;

@ExtendWith(MockitoExtension.class)
class DumAssetsSourceApiTest {

    @Mock
    private DumAssetsSourceRepository repository;
    @Mock
    private DumAssetsPluginProcess pluginProcess;
    @Mock
    private DumAssetsExchangeProcess exchangeProcess;

    private DumAssetsSourceApi api;

    @BeforeEach
    void setUp() {
        api = new DumAssetsSourceApi(repository, pluginProcess, exchangeProcess);
    }

    @Test
    void listReturnsAll() {
        when(repository.findAll()).thenReturn(List.of(new DumAssetsSource()));
        assertThat(api.list()).hasSize(1);
    }

    @Test
    void structureReturnsDefaults() {
        DumAssetsSource s = api.structure();
        assertThat(s.getLocale()).isEqualTo("en_US");
        assertThat(s.getContentType()).isEqualTo("Static File");
        assertThat(s.getChunk()).isEqualTo(100);
    }

    @Test
    void getReturnsExistingOrEmpty() {
        when(repository.findById("a"))
                .thenReturn(Optional.of(DumAssetsSource.builder().name("Docs").build()));
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThat(api.get("a").getName()).isEqualTo("Docs");
        assertThat(api.get("missing").getName()).isNull();
    }

    @Test
    void updateRejectsMismatchedId() {
        DumAssetsSourceRequest req = new DumAssetsSourceRequest();
        req.setId("other");
        assertThat(api.update("a", req).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(repository, never()).save(any());
    }

    @Test
    void updateAppliesFieldsWhenFound() {
        DumAssetsSource existing = DumAssetsSource.builder().id("a").build();
        when(repository.findById("a")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DumAssetsSourceRequest req = new DumAssetsSourceRequest();
        req.setName("Renamed");
        req.setSourceDir("/x");

        ResponseEntity<DumAssetsSource> response = api.update("a", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(existing.getName()).isEqualTo("Renamed");
        assertThat(existing.getSourceDir()).isEqualTo("/x");
    }

    @Test
    void updateReturnsNotFoundWhenMissing() {
        when(repository.findById("a")).thenReturn(Optional.empty());
        assertThat(api.update("a", new DumAssetsSourceRequest()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteReturnsTrueWhenPresentFalseOtherwise() {
        DumAssetsSource existing = DumAssetsSource.builder().id("a").build();
        when(repository.findById("a")).thenReturn(Optional.of(existing));
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThat(api.delete("a")).isTrue();
        verify(repository).delete(existing);
        assertThat(api.delete("missing")).isFalse();
    }

    @Test
    void createCreatesAndSaves() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DumAssetsSourceRequest req = new DumAssetsSourceRequest();
        req.setName("New");

        assertThat(api.create(req).getName()).isEqualTo("New");
        verify(repository).save(any(DumAssetsSource.class));
    }

    @Test
    void indexAllDelegates() {
        assertThat(api.indexAll("a").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(pluginProcess).indexAllByIdAsync("a");
    }

    @Test
    void exportAndImportDelegate() {
        api.export(null);
        verify(exchangeProcess).exportObject(null);
        assertThat(api.importSources(null).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(exchangeProcess).importFromMultipartFile(null);
    }
}
