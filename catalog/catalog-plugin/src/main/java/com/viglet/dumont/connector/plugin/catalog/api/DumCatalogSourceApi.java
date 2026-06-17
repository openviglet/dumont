/*
 *
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

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.dumont.connector.plugin.catalog.DumCatalogIndexer;
import com.viglet.dumont.connector.plugin.catalog.export.DumCatalogExchangeProcess;
import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogSource;
import com.viglet.dumont.connector.plugin.catalog.persistence.repository.DumCatalogSourceRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v2/catalog/source")
@Tag(name = "Catalog Source", description = "Catalog Bridge Source")
public class DumCatalogSourceApi {

    private final DumCatalogSourceRepository sourceRepository;
    private final DumCatalogIndexer indexer;
    private final DumCatalogExchangeProcess exchangeProcess;

    public DumCatalogSourceApi(DumCatalogSourceRepository sourceRepository,
            DumCatalogIndexer indexer, DumCatalogExchangeProcess exchangeProcess) {
        this.sourceRepository = sourceRepository;
        this.indexer = indexer;
        this.exchangeProcess = exchangeProcess;
    }

    @GetMapping
    @Operation(summary = "List all Catalog Sources")
    public List<DumCatalogSource> catalogSourceList() {
        return sourceRepository.findAll();
    }

    @GetMapping("/structure")
    @Operation(summary = "Catalog Source structure")
    public DumCatalogSource catalogSourceStructure() {
        DumCatalogSource source = new DumCatalogSource();
        source.setLocale("pt_BR");
        return source;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Show a Catalog Source")
    public DumCatalogSource catalogSourceGet(@PathVariable String id) {
        return sourceRepository.findById(id).orElse(new DumCatalogSource());
    }

    @Transactional
    @PutMapping("/{id}")
    @Operation(summary = "Update a Catalog Source")
    public ResponseEntity<DumCatalogSource> catalogSourceUpdate(@PathVariable String id,
            @RequestBody DumCatalogSource catalogSource) {
        if (catalogSource.getId() != null && !id.equals(catalogSource.getId())) {
            return ResponseEntity.badRequest().build();
        }
        return sourceRepository.findById(id).map(existing -> {
            existing.setName(catalogSource.getName());
            existing.setDescription(catalogSource.getDescription());
            existing.setCatalogUrl(catalogSource.getCatalogUrl());
            existing.setAuthHeaderName(catalogSource.getAuthHeaderName());
            existing.setAuthHeaderValue(catalogSource.getAuthHeaderValue());
            existing.setLocale(catalogSource.getLocale());
            existing.getTurSNSites().clear();
            if (catalogSource.getTurSNSites() != null) {
                existing.getTurSNSites().addAll(catalogSource.getTurSNSites());
            }
            return ResponseEntity.ok(sourceRepository.save(existing));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Transactional
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a Catalog Source")
    public boolean catalogSourceDelete(@PathVariable String id) {
        return sourceRepository.findById(id).map(source -> {
            sourceRepository.delete(source);
            return true;
        }).orElse(false);
    }

    @PostMapping
    @Operation(summary = "Create a Catalog Source")
    public DumCatalogSource catalogSourceAdd(@RequestBody DumCatalogSource catalogSource) {
        return sourceRepository.save(catalogSource);
    }

    @GetMapping("/{id}/indexAll")
    @Operation(summary = "Index all records from a Catalog Source")
    public ResponseEntity<Object> sourceIndexAll(@PathVariable String id) {
        indexer.indexAllByIdAsync(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Export all Catalog Sources")
    public ResponseEntity<String> exportSources() {
        return ResponseEntity.ok(exchangeProcess.exportJson());
    }

    @PostMapping("/import")
    @Operation(summary = "Import Catalog Sources from a JSON export")
    public ResponseEntity<Object> importSources(@RequestParam("file") MultipartFile file) {
        exchangeProcess.importFromMultipartFile(file);
        return ResponseEntity.ok().build();
    }
}
