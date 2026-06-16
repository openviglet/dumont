/*
 *
 * Copyright (C) 2016-2025 the original author or authors.
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

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.dumont.connector.plugin.DumConnectorPluginRegistry;
import com.viglet.dumont.connector.domain.DumConnectorMonitoring;
import com.viglet.dumont.connector.domain.DumConnectorMonitoringPage;
import com.viglet.dumont.connector.domain.DumConnectorMonitoringRequest;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingStatsModel;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;

import io.swagger.v3.oas.annotations.tags.Tag;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v2/connector/monitoring/indexing")
@Tag(name = "Connector API", description = "Connector API")
public class DumConnectorMonitoringApi {
        private final DumConnectorIndexingService indexingService;
        private final DumConnectorPluginRegistry pluginRegistry;

        public DumConnectorMonitoringApi(DumConnectorIndexingService indexingService,
                        DumConnectorPluginRegistry pluginRegistry) {
                this.indexingService = indexingService;
                this.pluginRegistry = pluginRegistry;
        }

        @GetMapping("unprocessed/{source}")
        public ResponseEntity<DumConnectorMonitoring> monitoringUnprocessed(
                        @PathVariable String source) {
                List<DumConnectorIndexingModel> indexing = indexingService
                                .getUnprocessedBySourceAndProvider(source,
                                                pluginRegistry.resolveProviderBySource(source));
                return generateMonitoringResponse(indexing);
        }

        @GetMapping
        public ResponseEntity<DumConnectorMonitoring> monitoringIndexing() {
                List<DumConnectorIndexingModel> indexing = indexingService.findAll();
                return generateMonitoringResponse(indexing);
        }

        @GetMapping("{source}")
        public ResponseEntity<DumConnectorMonitoring> monitoringIndexingBySource(
                        @PathVariable String source) {
                List<DumConnectorIndexingModel> indexing = indexingService
                                .getBySourceAndProvider(source,
                                                pluginRegistry.resolveProviderBySource(source));
                return generateMonitoringResponse(indexing);
        }

        @GetMapping("item/{id}")
        public ResponseEntity<DumConnectorIndexingModel> monitoringIndexingItem(
                        @PathVariable int id) {
                return indexingService.findById(id)
                                .map(ResponseEntity::ok)
                                .orElseGet(() -> ResponseEntity.notFound().build());
        }

        @PostMapping
        public ResponseEntity<DumConnectorMonitoring> indexContentId(
                        @RequestBody List<String> contentIds) {
                // Across all active providers (null provider = no provider filter).
                List<DumConnectorIndexingModel> indexing = indexingService.findAllByProviderAndObjectIdIn(
                                null, contentIds);
                return generateMonitoringResponse(indexing);
        }

        @PostMapping("_search")
        public ResponseEntity<DumConnectorMonitoringPage> search(
                        @RequestBody DumConnectorMonitoringRequest request) {
                // null provider = search across all active providers.
                return ResponseEntity.ok(indexingService.search(request, null));
        }

        @GetMapping("stats")
        public ResponseEntity<List<DumConnectorIndexingStatsModel>> monitoringStats() {
                return ResponseEntity.ok(indexingService.getAllStats());
        }

        @GetMapping("stats/count")
        public ResponseEntity<Map<String, Long>> monitoringStatsCount() {
                // null provider = count across all active providers.
                return ResponseEntity.ok(indexingService.countByProviderGroupBySource(null));
        }

        @GetMapping("stats/{source}")
        public ResponseEntity<List<DumConnectorIndexingStatsModel>> monitoringStatsBySource(
                        @PathVariable String source) {
                return ResponseEntity.ok(indexingService.getStatsBySourceAndProvider(source,
                                pluginRegistry.resolveProviderBySource(source)));
        }

        private ResponseEntity<DumConnectorMonitoring> generateMonitoringResponse(
                        List<DumConnectorIndexingModel> indexing) {
                return ResponseEntity.ok(indexing.isEmpty() ? new DumConnectorMonitoring()
                                : getMonitoring(indexing));
        }

        private DumConnectorMonitoring getMonitoring(List<DumConnectorIndexingModel> indexing) {
                // null provider = list sources across all active providers.
                return DumConnectorMonitoring.builder()
                                .sources(indexingService.getAllSources(null))
                                .indexing(indexing).build();
        }
}
