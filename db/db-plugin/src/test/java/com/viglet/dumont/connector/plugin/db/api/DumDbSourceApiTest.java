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

package com.viglet.dumont.connector.plugin.db.api;

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

import com.viglet.dumont.connector.plugin.db.DumDbPluginProcess;
import com.viglet.dumont.connector.plugin.db.api.dto.DumDbSourceRequest;
import com.viglet.dumont.connector.plugin.db.export.DumDbExchangeProcess;
import com.viglet.dumont.connector.plugin.db.persistence.model.DumDbSource;
import com.viglet.dumont.connector.plugin.db.persistence.repository.DumDbSourceRepository;

@ExtendWith(MockitoExtension.class)
class DumDbSourceApiTest {

    @Mock
    private DumDbSourceRepository repository;
    @Mock
    private DumDbPluginProcess pluginProcess;
    @Mock
    private DumDbExchangeProcess exchangeProcess;

    private DumDbSourceApi api;

    @BeforeEach
    void setUp() {
        api = new DumDbSourceApi(repository, pluginProcess, exchangeProcess);
    }

    @Test
    void listReturnsAllSources() {
        when(repository.findAll()).thenReturn(List.of(new DumDbSource(), new DumDbSource()));
        assertThat(api.dumDbSourceList()).hasSize(2);
    }

    @Test
    void structureReturnsDefaults() {
        DumDbSource s = api.dumDbSourceStructure();
        assertThat(s.getLocale()).isEqualTo("en_US");
        assertThat(s.getChunk()).isEqualTo(100);
        assertThat(s.getPrimaryKeyColumn()).isEqualTo("id");
    }

    @Test
    void getReturnsExistingOrEmpty() {
        DumDbSource existing = DumDbSource.builder().id("a").name("People").build();
        when(repository.findById("a")).thenReturn(Optional.of(existing));
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThat(api.dumDbSourceGet("a").getName()).isEqualTo("People");
        assertThat(api.dumDbSourceGet("missing").getName()).isNull();
    }

    @Test
    void updateRejectsMismatchedId() {
        DumDbSourceRequest req = new DumDbSourceRequest();
        req.setId("other");
        ResponseEntity<DumDbSource> response = api.dumDbSourceUpdate("a", req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(repository, never()).save(any());
    }

    @Test
    void updateAppliesFieldsWhenFound() {
        DumDbSource existing = DumDbSource.builder().id("a").build();
        when(repository.findById("a")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DumDbSourceRequest req = new DumDbSourceRequest();
        req.setName("Renamed");
        req.setChunk(7);

        ResponseEntity<DumDbSource> response = api.dumDbSourceUpdate("a", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(existing.getName()).isEqualTo("Renamed");
        assertThat(existing.getChunk()).isEqualTo(7);
    }

    @Test
    void updateReturnsNotFoundWhenMissing() {
        when(repository.findById("a")).thenReturn(Optional.empty());
        ResponseEntity<DumDbSource> response = api.dumDbSourceUpdate("a", new DumDbSourceRequest());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteReturnsTrueWhenPresentFalseOtherwise() {
        DumDbSource existing = DumDbSource.builder().id("a").build();
        when(repository.findById("a")).thenReturn(Optional.of(existing));
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThat(api.dumDbSourceDelete("a")).isTrue();
        verify(repository).delete(existing);
        assertThat(api.dumDbSourceDelete("missing")).isFalse();
    }

    @Test
    void addCreatesAndSaves() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DumDbSourceRequest req = new DumDbSourceRequest();
        req.setName("New");

        DumDbSource created = api.dumDbSourceAdd(req);

        assertThat(created.getName()).isEqualTo("New");
        verify(repository).save(any(DumDbSource.class));
    }

    @Test
    void indexAllDelegatesToPluginProcess() {
        ResponseEntity<Object> response = api.sourceIndexAll("a");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(pluginProcess).indexAllByIdAsync("a");
    }

    @Test
    void exportAndImportDelegateToExchangeProcess() {
        api.exportSources(null);
        verify(exchangeProcess).exportObject(null);

        ResponseEntity<Object> response = api.importSources(null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(exchangeProcess).importFromMultipartFile(null);
    }
}
