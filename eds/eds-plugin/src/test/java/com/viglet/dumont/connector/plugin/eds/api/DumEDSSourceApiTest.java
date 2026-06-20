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

package com.viglet.dumont.connector.plugin.eds.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.viglet.dumont.connector.plugin.eds.DumEDSPluginProcess;
import com.viglet.dumont.connector.plugin.eds.api.dto.DumEDSSourceRequest;
import com.viglet.dumont.connector.plugin.eds.export.DumEDSExchangeProcess;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSExcludePattern;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSFacet;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSFieldMapping;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSIndex;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSRemoveSelector;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSExcludePatternRepository;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSFacetRepository;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSFieldMappingRepository;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSIndexRepository;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSRemoveSelectorRepository;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSSourceRepository;

@ExtendWith(MockitoExtension.class)
class DumEDSSourceApiTest {

    @Mock
    private DumEDSSourceRepository dumEDSSourceRepository;
    @Mock
    private DumEDSIndexRepository dumEDSIndexRepository;
    @Mock
    private DumEDSExcludePatternRepository dumEDSExcludePatternRepository;
    @Mock
    private DumEDSRemoveSelectorRepository dumEDSRemoveSelectorRepository;
    @Mock
    private DumEDSFacetRepository dumEDSFacetRepository;
    @Mock
    private DumEDSFieldMappingRepository dumEDSFieldMappingRepository;
    @Mock
    private DumEDSPluginProcess dumEDSPluginProcess;
    @Mock
    private DumEDSExchangeProcess dumEDSExchangeProcess;

    private DumEDSSourceApi api;

    @BeforeEach
    void setUp() {
        api = new DumEDSSourceApi(dumEDSSourceRepository, dumEDSIndexRepository,
                dumEDSExcludePatternRepository, dumEDSRemoveSelectorRepository, dumEDSFacetRepository,
                dumEDSFieldMappingRepository, dumEDSPluginProcess, dumEDSExchangeProcess);
    }

    @Test
    void listAndStructure() {
        when(dumEDSSourceRepository.findAll()).thenReturn(List.of(new DumEDSSource()));
        assertThat(api.list()).hasSize(1);
        assertThat(api.structure()).isNotNull();
    }

    @Test
    void getHydratesAllChildCollections() {
        DumEDSSource source = new DumEDSSource();
        source.setId("a");
        source.setTitle("Example");
        when(dumEDSSourceRepository.findById("a")).thenReturn(Optional.of(source));
        when(dumEDSIndexRepository.findByDumEDSSource(source))
                .thenReturn(Optional.of(Set.of(new DumEDSIndex())));
        when(dumEDSExcludePatternRepository.findByDumEDSSource(source))
                .thenReturn(Optional.of(Set.of(new DumEDSExcludePattern())));
        when(dumEDSRemoveSelectorRepository.findByDumEDSSource(source))
                .thenReturn(Optional.of(Set.of(new DumEDSRemoveSelector())));
        when(dumEDSFacetRepository.findByDumEDSSource(source))
                .thenReturn(Optional.of(Set.of(new DumEDSFacet())));
        when(dumEDSFieldMappingRepository.findByDumEDSSource(source))
                .thenReturn(Optional.of(Set.of(new DumEDSFieldMapping())));

        DumEDSSource result = api.get("a");

        assertThat(result.getTitle()).isEqualTo("Example");
        assertThat(result.getIndexes()).hasSize(1);
        assertThat(result.getFieldMappings()).hasSize(1);
    }

    @Test
    void getReturnsEmptyWhenMissing() {
        when(dumEDSSourceRepository.findById("missing")).thenReturn(Optional.empty());
        assertThat(api.get("missing").getTitle()).isNull();
    }

    @Test
    void updateRejectsMismatchedId() {
        DumEDSSourceRequest req = new DumEDSSourceRequest();
        req.setId("other");
        assertThat(api.update("a", req).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(dumEDSSourceRepository, never()).save(any());
    }

    @Test
    void updateAppliesFieldsAndReparentsChildren() {
        DumEDSSource existing = DumEDSSource.builder().id("a").build();
        when(dumEDSSourceRepository.findById("a")).thenReturn(Optional.of(existing));
        when(dumEDSSourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DumEDSSourceRequest req = new DumEDSSourceRequest();
        req.setTitle("Example");
        req.setBaseUrl("https://example.invalid");
        req.setIndexes(Set.of(new DumEDSIndex()));
        req.setExcludePatterns(Set.of(new DumEDSExcludePattern()));
        req.setRemoveSelectors(Set.of(new DumEDSRemoveSelector()));
        req.setFacets(Set.of(new DumEDSFacet()));
        req.setFieldMappings(Set.of(new DumEDSFieldMapping()));

        ResponseEntity<DumEDSSource> response = api.update("a", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(existing.getBaseUrl()).isEqualTo("https://example.invalid");
        assertThat(existing.getIndexes())
                .allSatisfy(idx -> assertThat(idx.getDumEDSSource()).isSameAs(existing));
    }

    @Test
    void updateReturnsNotFoundWhenMissing() {
        when(dumEDSSourceRepository.findById("a")).thenReturn(Optional.empty());
        assertThat(api.update("a", new DumEDSSourceRequest()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteReturnsTrueWhenPresentFalseOtherwise() {
        DumEDSSource existing = DumEDSSource.builder().id("a").build();
        when(dumEDSSourceRepository.findById("a")).thenReturn(Optional.of(existing));
        when(dumEDSSourceRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(api.delete("a")).isTrue();
        verify(dumEDSSourceRepository).delete(existing);
        assertThat(api.delete("missing")).isFalse();
    }

    @Test
    void createBuildsAndSaves() {
        when(dumEDSSourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DumEDSSourceRequest req = new DumEDSSourceRequest();
        req.setTitle("New");
        assertThat(api.create(req).getTitle()).isEqualTo("New");
        verify(dumEDSSourceRepository).save(any(DumEDSSource.class));
    }

    @Test
    void indexAndReindexStartTheProcessWhenPresent() {
        DumEDSSource existing = DumEDSSource.builder().id("a").build();
        when(dumEDSSourceRepository.findById("a")).thenReturn(Optional.of(existing));

        assertThat(api.index("a").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(dumEDSPluginProcess).start(existing);

        assertThat(api.reindex("a").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(dumEDSPluginProcess).reindex(existing);
    }

    @Test
    void dryScanReturnsOkWhenPresentNotFoundOtherwise() {
        DumEDSSource existing = DumEDSSource.builder().id("a").build();
        when(dumEDSSourceRepository.findById("a")).thenReturn(Optional.of(existing));
        when(dumEDSSourceRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(api.dryScan("a").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(dumEDSPluginProcess).dryScan(existing);
        assertThat(api.dryScan("missing").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void exportAndImport() {
        api.export(null);
        verify(dumEDSExchangeProcess).exportObject(null);
        assertThat(api.importSources(null).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(dumEDSExchangeProcess).importFromMultipartFile(null);
    }
}
