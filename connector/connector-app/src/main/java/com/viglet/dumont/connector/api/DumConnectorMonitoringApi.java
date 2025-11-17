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

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.domain.DumConnectorMonitoring;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v2/connector/monitoring/indexing")
@Tag(name = "Connector API", description = "Connector API")
public class DumConnectorMonitoringApi {
        private final DumConnectorIndexingService indexingService;
        private final DumConnectorPlugin plugin;

        public DumConnectorMonitoringApi(DumConnectorIndexingService indexingService,
                        DumConnectorPlugin plugin) {
                this.indexingService = indexingService;
                this.plugin = plugin;
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
                                .getBySourceAndProvider(source, plugin.getProviderName());
                return generateMonitoringResponse(indexing);
        }

        @PostMapping
        public ResponseEntity<DumConnectorMonitoring> indexContentId(
                        @RequestBody List<String> contentIds) {
                List<DumConnectorIndexingModel> indexing = indexingService.findAllByProviderAndObjectIdIn(
                                plugin.getProviderName(), contentIds);
                return generateMonitoringResponse(indexing);
        }

        private ResponseEntity<DumConnectorMonitoring> generateMonitoringResponse(
                        List<DumConnectorIndexingModel> indexing) {
                return ResponseEntity.ok(indexing.isEmpty() ? new DumConnectorMonitoring()
                                : getMonitoring(indexing));
        }

        private DumConnectorMonitoring getMonitoring(List<DumConnectorIndexingModel> indexing) {
                return DumConnectorMonitoring.builder()
                                .sources(indexingService.getAllSources(plugin.getProviderName()))
                                .indexing(indexing).build();
        }
}
