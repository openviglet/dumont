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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.plugin.DumConnectorPluginRegistry;
import com.viglet.dumont.connector.domain.DumConnectorValidateDifference;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingStatsModel.OperationType;
import com.viglet.dumont.connector.scheduled.DumConnectorContentAuditTask;
import com.viglet.dumont.connector.service.DumConnectorIndexAllByTabService;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.dumont.connector.service.DumConnectorRunService;
import com.viglet.dumont.connector.service.DumConnectorRunService.RunStart;
import com.viglet.dumont.connector.service.DumConnectorSolrService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v2/connector")
@Tag(name = "Connector API", description = "Connector API")
public class DumConnectorApi {
    private static final String STATUS_KEY = "status";
    private final DumConnectorIndexingService indexingService;
    private final DumConnectorSolrService dumConnectorSolr;
    private final DumConnectorPluginRegistry pluginRegistry;
    private final DumConnectorContentAuditTask auditTask;
    private final DumConnectorIndexAllByTabService indexAllByTabService;
    private final DumConnectorRunService runService;

    public DumConnectorApi(DumConnectorIndexingService indexingService,
            DumConnectorSolrService dumConnectorSolr, DumConnectorPluginRegistry pluginRegistry,
            DumConnectorContentAuditTask auditTask,
            DumConnectorIndexAllByTabService indexAllByTabService,
            DumConnectorRunService runService) {
        this.indexingService = indexingService;
        this.dumConnectorSolr = dumConnectorSolr;
        this.pluginRegistry = pluginRegistry;
        this.auditTask = auditTask;
        this.indexAllByTabService = indexAllByTabService;
        this.runService = runService;
    }

    /**
     * The unauthenticated probe: the key filter and the security chain both let it through, so it
     * answers liveness and the active providers and nothing a caller could act as Dumont with (D142).
     * It used to carry {@code turing.apiKey}, read by the console to set a {@code Key} header it
     * never needed — the console authenticates by session, and Turing's integration proxy sets its
     * own key.
     */
    @GetMapping("status")
    public Map<String, Object> status() {
        List<String> providers = pluginRegistry.providerNames();
        Map<String, Object> status = new HashMap<>();
        status.put(STATUS_KEY, "ok");
        // Backward-compatible single value: first active provider (null when none active).
        status.put("provider", providers.isEmpty() ? null : providers.getFirst());
        status.put("providers", providers);
        return status;
    }

    private static Map<String, String> statusSent() {
        Map<String, String> status = new HashMap<>();
        status.put(STATUS_KEY, "sent");
        return status;
    }

    private static Map<String, String> statusAlreadyRunning() {
        Map<String, String> status = new HashMap<>();
        status.put(STATUS_KEY, "already running");
        return status;
    }

    @GetMapping("validate/{source}")
    public DumConnectorValidateDifference validateSource(@PathVariable String source) {
        return dumConnectorSolr.validateContent(source,
                pluginRegistry.resolveProviderBySource(source));
    }

    @GetMapping("monitoring/index/{source}")
    public ResponseEntity<List<DumConnectorIndexingModel>> monitoryIndexByName(
            @PathVariable String source) {
        List<DumConnectorIndexingModel> indexingModelList = indexingService.getBySourceAndProvider(source,
                pluginRegistry.resolveProviderBySource(source));
        return indexingModelList.isEmpty() ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(indexingModelList);
    }

    private static Map<String, String> statusUnknownSource(String name) {
        Map<String, String> status = new HashMap<>();
        status.put(STATUS_KEY, "unknown source");
        status.put("reason", "No connector plugin manages the source '" + name + "'");
        return status;
    }

    /**
     * Starts a whole-source run. The owner is resolved before the lock is taken, so a name no
     * plugin manages answers 404 instead of locking itself (D124); the start itself is
     * {@link DumConnectorRunService}'s, shared with the scheduler (D128).
     */
    private ResponseEntity<Map<String, String>> startRun(String name, OperationType operation) {
        Optional<DumConnectorPlugin> owner = pluginRegistry.findBySource(name);
        if (owner.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(statusUnknownSource(name));
        }
        if (runService.start(owner.get(), name, operation) == RunStart.ALREADY_RUNNING) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(statusAlreadyRunning());
        }
        return ResponseEntity.ok(statusSent());
    }

    @GetMapping("index/{name}/all")
    public ResponseEntity<Map<String, String>> indexAll(@PathVariable String name) {
        return startRun(name, OperationType.INDEX_ALL);
    }

    @PostMapping("index/{name}")
    public ResponseEntity<Map<String, String>> indexContentId(@PathVariable String name,
            @RequestBody List<String> contentId) {
        pluginRegistry.resolveBySource(name).indexById(name, contentId);
        return ResponseEntity.ok(statusSent());
    }

    @GetMapping("index/{name}")
    public ResponseEntity<Map<String, String>> indexContentId(@PathVariable String name,
            @RequestParam String id) {
        pluginRegistry.resolveBySource(name).indexById(name, List.of(id));
        return ResponseEntity.ok(statusSent());
    }

    @GetMapping("reindex/{name}/all")
    public ResponseEntity<Map<String, String>> reindexAll(@PathVariable String name) {
        return startRun(name, OperationType.REINDEX_ALL);
    }

    @PostMapping("reindex/{name}")
    public ResponseEntity<Map<String, String>> reindexAll(@PathVariable String name,
            @RequestBody List<String> contentIds) {
        DumConnectorPlugin plugin = pluginRegistry.resolveBySource(name);
        indexingService.deleteByProviderAndSourceAndObjectIdIn(plugin.getProviderName(), name,
                contentIds);
        plugin.indexById(name, contentIds);
        return ResponseEntity.ok(statusSent());
    }

    @GetMapping("reindex/{name}")
    public ResponseEntity<Map<String, String>> reindexAll(@PathVariable String name,
            @RequestParam String id) {
        DumConnectorPlugin plugin = pluginRegistry.resolveBySource(name);
        indexingService.deleteByProviderAndSourceAndObjectIdIn(plugin.getProviderName(), name,
                List.of(id));
        plugin.indexById(name, List.of(id));
        return ResponseEntity.ok(statusSent());
    }

    @PostMapping("index-all-by-tab/{source}")
    public ResponseEntity<Map<String, String>> indexAllByTab(@PathVariable String source,
            @RequestBody Map<String, String> body) {
        String tab = body.getOrDefault("tab", "");
        indexAllByTabService.indexAllByTabAsync(source, tab);
        return ResponseEntity.ok(statusSent());
    }

    @GetMapping("audit/{source}")
    public ResponseEntity<Map<String, String>> auditSource(@PathVariable String source) {
        auditTask.auditSourceAsync(source);
        return ResponseEntity.ok(statusSent());
    }

}
