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

package com.viglet.dumont.connector.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.domain.DumConnectorValidateDifference;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import com.viglet.dumont.connector.plugin.DumConnectorPluginRegistry;
import com.viglet.dumont.connector.scheduled.DumConnectorContentAuditTask;
import com.viglet.dumont.connector.service.DumConnectorIndexAllByTabService;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.dumont.connector.service.DumConnectorRunService;
import com.viglet.dumont.connector.service.DumConnectorSolrService;

@ExtendWith(MockitoExtension.class)
class DumConnectorApiTest {

    @Mock
    private DumConnectorIndexingService indexingService;
    @Mock
    private DumConnectorSolrService dumConnectorSolr;
    @Mock
    private DumConnectorPluginRegistry pluginRegistry;
    @Mock
    private DumConnectorContentAuditTask auditTask;
    @Mock
    private DumConnectorIndexAllByTabService indexAllByTabService;
    @Mock
    private DumConnectorPlugin plugin;

    private DumConnectorApi api;

    @BeforeEach
    void setUp() {
        api = new DumConnectorApi(indexingService, dumConnectorSolr, pluginRegistry, auditTask,
                indexAllByTabService, new DumConnectorRunService(indexingService), "secret-key");
    }

    @Test
    void statusReportsProvidersAndApiKey() {
        when(pluginRegistry.providerNames()).thenReturn(List.of("turing", "solr"));
        Map<String, Object> status = api.status();
        assertThat(status).containsEntry("status", "ok").containsEntry("provider", "turing")
                .containsEntry("apiKey", "secret-key")
                .containsEntry("providers", List.of("turing", "solr"));
    }

    @Test
    void statusProviderIsNullWhenNoneActive() {
        when(pluginRegistry.providerNames()).thenReturn(List.of());
        assertThat(api.status().get("provider")).isNull();
    }

    @Test
    void validateSourceDelegatesToSolrService() {
        when(pluginRegistry.resolveProviderBySource("s")).thenReturn("turing");
        DumConnectorValidateDifference diff = DumConnectorValidateDifference.builder().build();
        when(dumConnectorSolr.validateContent("s", "turing")).thenReturn(diff);
        assertThat(api.validateSource("s")).isSameAs(diff);
    }

    @Test
    void monitoringReturnsNotFoundWhenEmpty() {
        when(pluginRegistry.resolveProviderBySource("s")).thenReturn("turing");
        when(indexingService.getBySourceAndProvider("s", "turing")).thenReturn(List.of());
        assertThat(api.monitoryIndexByName("s").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void monitoringReturnsOkWhenPresent() {
        when(pluginRegistry.resolveProviderBySource("s")).thenReturn("turing");
        when(indexingService.getBySourceAndProvider("s", "turing"))
                .thenReturn(List.of(DumConnectorIndexingModel.builder().objectId("o").build()));
        assertThat(api.monitoryIndexByName("s").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void indexAllReturnsConflictWhenAlreadyRunning() {
        when(pluginRegistry.findBySource("name")).thenReturn(Optional.of(plugin));
        when(indexingService.tryStartProcessing("name")).thenReturn(false);
        assertThat(api.indexAll("name").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(plugin, never()).indexAll("name");
    }

    @Test
    void indexAllStartsPluginWhenFree() {
        when(pluginRegistry.findBySource("name")).thenReturn(Optional.of(plugin));
        when(indexingService.tryStartProcessing("name")).thenReturn(true);
        when(plugin.getProviderName()).thenReturn("turing");

        assertThat(api.indexAll("name").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(plugin).indexAll("name");
        verify(indexingService).trackIndexingStart(eq("name"), eq("turing"),
                org.mockito.ArgumentMatchers.any());
        verify(indexingService, never()).finishProcessing("name");
    }

    @Test
    void indexAllOnUnownedSourceAnswersNotFoundWithoutTakingTheLock() {
        when(pluginRegistry.findBySource("ghost")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, String>> response = api.indexAll("ghost");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("status", "unknown source")
                .containsKey("reason");
        verify(indexingService, never()).tryStartProcessing("ghost");
        assertThat(api.reindexAll("ghost").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void indexAllReleasesTheLockWhenTheStartThrows() {
        when(pluginRegistry.findBySource("name")).thenReturn(Optional.of(plugin));
        when(indexingService.tryStartProcessing("name")).thenReturn(true);
        when(plugin.getProviderName()).thenReturn("turing");
        doThrow(new UnsupportedOperationException("boom")).when(plugin).indexAll("name");

        assertThatThrownBy(() -> api.indexAll("name"))
                .isInstanceOf(UnsupportedOperationException.class);
        verify(indexingService).finishProcessing("name");
    }

    @Test
    void reindexAllReleasesTheLockWhenTheStartThrows() {
        when(pluginRegistry.findBySource("name")).thenReturn(Optional.of(plugin));
        when(indexingService.tryStartProcessing("name")).thenReturn(true);
        when(plugin.getProviderName()).thenReturn("turing");
        doThrow(new IllegalStateException("boom")).when(indexingService)
                .deleteByProviderAndSource("turing", "name");

        assertThatThrownBy(() -> api.reindexAll("name"))
                .isInstanceOf(IllegalStateException.class);
        verify(indexingService).finishProcessing("name");
        verify(plugin, never()).indexAll("name");
    }

    @Test
    void indexContentIdByListAndByParam() {
        when(pluginRegistry.resolveBySource("name")).thenReturn(plugin);

        assertThat(api.indexContentId("name", List.of("c1", "c2")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        verify(plugin).indexById("name", List.of("c1", "c2"));

        assertThat(api.indexContentId("name", "c3").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(plugin).indexById("name", List.of("c3"));
    }

    @Test
    void reindexAllConflictAndSuccess() {
        when(pluginRegistry.findBySource("name")).thenReturn(Optional.of(plugin));
        when(indexingService.tryStartProcessing("name")).thenReturn(false);
        assertThat(api.reindexAll("name").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        when(indexingService.tryStartProcessing("free")).thenReturn(true);
        when(pluginRegistry.findBySource("free")).thenReturn(Optional.of(plugin));
        when(plugin.getProviderName()).thenReturn("turing");
        assertThat(api.reindexAll("free").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(indexingService).deleteByProviderAndSource("turing", "free");
        verify(plugin).indexAll("free");
    }

    @Test
    void reindexByListAndByParam() {
        when(pluginRegistry.resolveBySource("name")).thenReturn(plugin);
        when(plugin.getProviderName()).thenReturn("turing");

        api.reindexAll("name", List.of("c1"));
        verify(indexingService).deleteByProviderAndSourceAndObjectIdIn("turing", "name",
                List.of("c1"));
        verify(plugin).indexById("name", List.of("c1"));

        api.reindexAll("name", "c2");
        verify(plugin).indexById("name", List.of("c2"));
    }

    @Test
    void indexAllByTabUsesTabFromBodyOrDefault() {
        assertThat(api.indexAllByTab("s", Map.of("tab", "books")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        verify(indexAllByTabService).indexAllByTabAsync("s", "books");

        api.indexAllByTab("s", Map.of());
        verify(indexAllByTabService).indexAllByTabAsync("s", "");
    }

    @Test
    void auditSourceDelegatesToAuditTask() {
        assertThat(api.auditSource("s").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(auditTask).auditSourceAsync("s");
    }
}
