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

package com.viglet.dumont.connector.plugin.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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

import com.viglet.dumont.connector.commons.spi.structured.parity.DumParityCriteria;
import com.viglet.dumont.connector.commons.spi.structured.parity.DumParityReport;
import com.viglet.dumont.connector.plugin.catalog.DumCatalogIndexer;
import com.viglet.dumont.connector.plugin.catalog.api.dto.DumCatalogSourceRequest;
import com.viglet.dumont.connector.plugin.catalog.export.DumCatalogExchangeProcess;
import com.viglet.dumont.connector.plugin.catalog.migration.DumCatalogParityService;
import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogSource;
import com.viglet.dumont.connector.plugin.catalog.persistence.repository.DumCatalogSourceRepository;

@ExtendWith(MockitoExtension.class)
class DumCatalogSourceApiTest {

    @Mock
    private DumCatalogSourceRepository repository;
    @Mock
    private DumCatalogIndexer indexer;
    @Mock
    private DumCatalogExchangeProcess exchangeProcess;
    @Mock
    private DumCatalogParityService parityService;

    private DumCatalogSourceApi api;

    @BeforeEach
    void setUp() {
        api = new DumCatalogSourceApi(repository, indexer, exchangeProcess, parityService);
    }

    @Test
    void listAndStructure() {
        when(repository.findAll()).thenReturn(List.of(new DumCatalogSource()));
        assertThat(api.catalogSourceList()).hasSize(1);
        assertThat(api.catalogSourceStructure().getLocale()).isEqualTo("pt_BR");
    }

    @Test
    void getReturnsExistingOrEmpty() {
        when(repository.findById("a"))
                .thenReturn(Optional.of(DumCatalogSource.builder().name("UFRJ").build()));
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThat(api.catalogSourceGet("a").getName()).isEqualTo("UFRJ");
        assertThat(api.catalogSourceGet("missing").getName()).isNull();
    }

    @Test
    void updateRejectsMismatchedId() {
        DumCatalogSourceRequest req = new DumCatalogSourceRequest();
        req.setId("other");
        assertThat(api.catalogSourceUpdate("a", req).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(repository, never()).save(any());
    }

    @Test
    void updateAppliesFieldsAndSites() {
        DumCatalogSource existing = DumCatalogSource.builder().id("a").build();
        when(repository.findById("a")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DumCatalogSourceRequest req = new DumCatalogSourceRequest();
        req.setName("UFRJ");
        req.setCatalogUrl("https://x/c.json");
        req.setTurSNSites(new java.util.HashSet<>(List.of("Catalog")));

        ResponseEntity<DumCatalogSource> response = api.catalogSourceUpdate("a", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(existing.getCatalogUrl()).isEqualTo("https://x/c.json");
        assertThat(existing.getTurSNSites()).containsExactly("Catalog");
    }

    @Test
    void updateNotFound() {
        when(repository.findById("a")).thenReturn(Optional.empty());
        assertThat(api.catalogSourceUpdate("a", new DumCatalogSourceRequest()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteAndAddAndIndex() {
        DumCatalogSource existing = DumCatalogSource.builder().id("a").build();
        when(repository.findById("a")).thenReturn(Optional.of(existing));
        when(repository.findById("missing")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(api.catalogSourceDelete("a")).isTrue();
        assertThat(api.catalogSourceDelete("missing")).isFalse();

        DumCatalogSourceRequest req = new DumCatalogSourceRequest();
        req.setName("New");
        assertThat(api.catalogSourceAdd(req).getName()).isEqualTo("New");

        assertThat(api.sourceIndexAll("a").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(indexer).indexAllByIdAsync("a");
    }

    @Test
    void parityReturnsOkWithCustomCriteria() {
        DumParityReport report = mock(DumParityReport.class);
        when(parityService.parity(eq("a"), any(DumParityCriteria.class))).thenReturn(report);

        ResponseEntity<DumParityReport> response = api.sourceParity("a", 0.1, 0.9, 25);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(report);
    }

    @Test
    void parityMapsExceptionsToStatuses() {
        when(parityService.parity(eq("missing"), any()))
                .thenThrow(new IllegalArgumentException("nope"));
        assertThat(api.sourceParity("missing", null, null, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        when(parityService.parity(eq("noBinding"), any()))
                .thenThrow(new IllegalStateException("no native"));
        assertThat(api.sourceParity("noBinding", null, null, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void migrateNativeDelegatesToGate() {
        DumParityReport report = mock(DumParityReport.class);
        when(parityService.migrateToNative(eq("a"), any())).thenReturn(report);
        assertThat(api.sourceMigrateNative("a", null, null, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void migrateSidecarReturnsOkOrNotFound() {
        DumCatalogSource source = DumCatalogSource.builder().id("a").build();
        when(parityService.rollbackToSidecar("a")).thenReturn(source);
        assertThat(api.sourceMigrateSidecar("a").getStatusCode()).isEqualTo(HttpStatus.OK);

        when(parityService.rollbackToSidecar("missing"))
                .thenThrow(new IllegalArgumentException("nope"));
        assertThat(api.sourceMigrateSidecar("missing").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void exportAndImport() {
        when(exchangeProcess.exportJson()).thenReturn("[]");
        assertThat(api.exportSources().getBody()).isEqualTo("[]");
        assertThat(api.importSources(null).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(exchangeProcess).importFromMultipartFile(null);
    }
}
