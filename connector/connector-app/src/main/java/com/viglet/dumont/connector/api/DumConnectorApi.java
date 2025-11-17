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

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.domain.DumConnectorValidateDifference;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.dumont.connector.service.DumConnectorSolrService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v2/connector")
@Tag(name = "Connector API", description = "Connector API")
public class DumConnectorApi {
    private final DumConnectorIndexingService indexingService;
    private final DumConnectorSolrService dumConnectorSolr;
    private final DumConnectorPlugin plugin;

    public DumConnectorApi(DumConnectorIndexingService indexingService,
            DumConnectorSolrService dumConnectorSolr, DumConnectorPlugin plugin) {
        this.indexingService = indexingService;
        this.dumConnectorSolr = dumConnectorSolr;
        this.plugin = plugin;
    }

    @GetMapping("status")
    public Map<String, String> status() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "ok");
        return status;
    }

    private static Map<String, String> statusSent() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "sent");
        return status;
    }

    @GetMapping("validate/{source}")
    public DumConnectorValidateDifference validateSource(@PathVariable String source) {
        return DumConnectorValidateDifference.builder()
                .missing(dumConnectorSolr.solrMissingContent(source, plugin.getProviderName()))
                .extra(dumConnectorSolr.solrExtraContent(source, plugin.getProviderName())).build();
    }

    @GetMapping("monitoring/index/{source}")
    public ResponseEntity<List<DumConnectorIndexingModel>> monitoryIndexByName(
            @PathVariable String source) {
        List<DumConnectorIndexingModel> indexingModelList = indexingService.getBySourceAndProvider(source,
                plugin.getProviderName());
        return indexingModelList.isEmpty() ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(indexingModelList);
    }

    @GetMapping("index/{name}/all")
    public ResponseEntity<Map<String, String>> indexAll(@PathVariable String name) {
        plugin.indexAll(name);
        return ResponseEntity.ok(statusSent());
    }

    @PostMapping("index/{name}")
    public ResponseEntity<Map<String, String>> indexContentId(@PathVariable String name,
            @RequestBody List<String> contentId) {
        plugin.indexById(name, contentId);
        return ResponseEntity.ok(statusSent());
    }

    @GetMapping("reindex/{name}/all")
    public ResponseEntity<Map<String, String>> reindexAll(@PathVariable String name) {
        indexingService.deleteByProviderAndSource(plugin.getProviderName(), name);
        plugin.indexAll(name);
        return ResponseEntity.ok(statusSent());
    }

    @GetMapping("reindex/{name}")
    public ResponseEntity<Map<String, String>> reindexAll(@PathVariable String name,
            @RequestBody List<String> contentIds) {
        indexingService.deleteByProviderAndSourceAndObjectIdIn(plugin.getProviderName(), name,
                contentIds);
        plugin.indexById(name, contentIds);
        return ResponseEntity.ok(statusSent());
    }

}
