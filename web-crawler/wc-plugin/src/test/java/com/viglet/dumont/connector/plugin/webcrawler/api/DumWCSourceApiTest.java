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

package com.viglet.dumont.connector.plugin.webcrawler.api;

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

import com.viglet.dumont.connector.plugin.webcrawler.DumWCPluginProcess;
import com.viglet.dumont.connector.plugin.webcrawler.api.dto.DumWCSourceRequest;
import com.viglet.dumont.connector.plugin.webcrawler.export.DumWCExchangeProcess;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCAllowUrl;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCAttributeMapping;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCFileExtension;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCNotAllowUrl;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCSource;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCStartingPoint;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCAllowUrlRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCAttributeMappingRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCFileExtensionRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCNotAllowUrlRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCSourceRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCStartingPointRepository;

@ExtendWith(MockitoExtension.class)
class DumWCSourceApiTest {

    @Mock
    private DumWCSourceRepository dumWCSourceRepository;
    @Mock
    private DumWCStartingPointRepository dumWCStartingPointRepository;
    @Mock
    private DumWCAllowUrlRepository dumWCAllowUrlRepository;
    @Mock
    private DumWCNotAllowUrlRepository dumWCNotAllowUrlRepository;
    @Mock
    private DumWCFileExtensionRepository dumWCFileExtensionRepository;
    @Mock
    private DumWCAttributeMappingRepository dumWCAttributeMappingRepository;
    @Mock
    private DumWCPluginProcess dumWCPluginProcess;
    @Mock
    private DumWCExchangeProcess dumWCExchangeProcess;

    private DumWCSourceApi api;

    @BeforeEach
    void setUp() {
        api = new DumWCSourceApi(dumWCSourceRepository, dumWCStartingPointRepository,
                dumWCAllowUrlRepository, dumWCNotAllowUrlRepository, dumWCFileExtensionRepository,
                dumWCAttributeMappingRepository, dumWCPluginProcess, dumWCExchangeProcess);
    }

    @Test
    void listAndStructure() {
        when(dumWCSourceRepository.findAll()).thenReturn(List.of(new DumWCSource()));
        assertThat(api.list()).hasSize(1);
        assertThat(api.structure()).isNotNull();
    }

    @Test
    void getHydratesAllChildCollections() {
        DumWCSource source = DumWCSource.builder().id("a").title("Wiki").build();
        when(dumWCSourceRepository.findById("a")).thenReturn(Optional.of(source));
        when(dumWCStartingPointRepository.findByDumWCSource(source))
                .thenReturn(Optional.of(List.of(new DumWCStartingPoint())));
        when(dumWCAllowUrlRepository.findByDumWCSource(source))
                .thenReturn(Optional.of(List.of(new DumWCAllowUrl())));
        when(dumWCNotAllowUrlRepository.findByDumWCSource(source))
                .thenReturn(Optional.of(List.of(new DumWCNotAllowUrl())));
        when(dumWCFileExtensionRepository.findByDumWCSource(source))
                .thenReturn(Optional.of(List.of(new DumWCFileExtension())));
        when(dumWCAttributeMappingRepository.findByDumWCSource(source))
                .thenReturn(Optional.of(List.of(new DumWCAttributeMapping())));

        DumWCSource result = api.get("a");

        assertThat(result.getTitle()).isEqualTo("Wiki");
        assertThat(result.getStartingPoints()).hasSize(1);
        assertThat(result.getAttributeMappings()).hasSize(1);
    }

    @Test
    void getReturnsEmptyWhenMissing() {
        when(dumWCSourceRepository.findById("missing")).thenReturn(Optional.empty());
        assertThat(api.get("missing").getTitle()).isNull();
    }

    @Test
    void updateRejectsMismatchedId() {
        DumWCSourceRequest req = new DumWCSourceRequest();
        req.setId("other");
        assertThat(api.update("a", req).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(dumWCSourceRepository, never()).save(any());
    }

    @Test
    void updateAppliesFieldsAndReparentsChildren() {
        DumWCSource existing = DumWCSource.builder().id("a").build();
        when(dumWCSourceRepository.findById("a")).thenReturn(Optional.of(existing));
        when(dumWCSourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DumWCSourceRequest req = new DumWCSourceRequest();
        req.setTitle("Wiki");
        req.setStartingPoints(List.of(new DumWCStartingPoint()));
        req.setAllowUrls(List.of(new DumWCAllowUrl()));
        req.setNotAllowUrls(List.of(new DumWCNotAllowUrl()));
        req.setNotAllowExtensions(List.of(new DumWCFileExtension()));
        req.setAttributeMappings(List.of(new DumWCAttributeMapping()));

        ResponseEntity<DumWCSource> response = api.update("a", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(existing.getTitle()).isEqualTo("Wiki");
        assertThat(existing.getStartingPoints()).allSatisfy(sp -> assertThat(sp.getDumWCSource())
                .isSameAs(existing));
    }

    @Test
    void updateReturnsNotFoundWhenMissing() {
        when(dumWCSourceRepository.findById("a")).thenReturn(Optional.empty());
        assertThat(api.update("a", new DumWCSourceRequest()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteReturnsTrueWhenPresentFalseOtherwise() {
        DumWCSource existing = DumWCSource.builder().id("a").build();
        when(dumWCSourceRepository.findById("a")).thenReturn(Optional.of(existing));
        when(dumWCSourceRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(api.delete("a")).isTrue();
        verify(dumWCSourceRepository).delete(existing);
        assertThat(api.delete("missing")).isFalse();
    }

    @Test
    void createBuildsAndSaves() {
        when(dumWCSourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DumWCSourceRequest req = new DumWCSourceRequest();
        req.setTitle("New");
        assertThat(api.create(req).getTitle()).isEqualTo("New");
        verify(dumWCSourceRepository).save(any(DumWCSource.class));
    }

    @Test
    void crawlStartsWhenPresent() {
        DumWCSource existing = DumWCSource.builder().id("a").build();
        when(dumWCSourceRepository.findById("a")).thenReturn(Optional.of(existing));

        assertThat(api.crawl("a").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(dumWCPluginProcess).start(existing);
    }

    @Test
    void crawlIsOkEvenWhenMissing() {
        when(dumWCSourceRepository.findById("missing")).thenReturn(Optional.empty());
        assertThat(api.crawl("missing").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(dumWCPluginProcess, never()).start(any());
    }

    @Test
    void exportAndImport() {
        api.export(null);
        verify(dumWCExchangeProcess).exportObject(null);
        assertThat(api.importSources(null).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(dumWCExchangeProcess).importFromMultipartFile(null);
    }
}
