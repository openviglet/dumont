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

import com.viglet.dumont.connector.commons.spi.structured.parity.DumParityCriteria;
import com.viglet.dumont.connector.commons.spi.structured.parity.DumParityReport;
import com.viglet.dumont.connector.plugin.catalog.DumCatalogIndexer;
import com.viglet.dumont.connector.plugin.catalog.export.DumCatalogExchangeProcess;
import com.viglet.dumont.connector.plugin.catalog.migration.DumCatalogParityService;
import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogSource;
import com.viglet.dumont.connector.plugin.catalog.persistence.repository.DumCatalogSourceRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v2/catalog/source")
@Tag(name = "Catalog Source", description = "Catalog Bridge Source")
public class DumCatalogSourceApi {

    private final DumCatalogSourceRepository sourceRepository;
    private final DumCatalogIndexer indexer;
    private final DumCatalogExchangeProcess exchangeProcess;
    private final DumCatalogParityService parityService;

    public DumCatalogSourceApi(DumCatalogSourceRepository sourceRepository,
            DumCatalogIndexer indexer, DumCatalogExchangeProcess exchangeProcess,
            DumCatalogParityService parityService) {
        this.sourceRepository = sourceRepository;
        this.indexer = indexer;
        this.exchangeProcess = exchangeProcess;
        this.parityService = parityService;
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
            // Native-migration binding (D11) is configurable here; the state flip itself is gated
            // by the parity endpoints, never set directly through a plain update.
            existing.setLeva(catalogSource.getLeva());
            existing.setNativeRecipe(catalogSource.getNativeRecipe());
            existing.setNativeListingUrl(catalogSource.getNativeListingUrl());
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

    @GetMapping("/{id}/parity")
    @Operation(summary = "Parity gate (D11): compare this source's native recipe vs the sidecar run")
    public ResponseEntity<DumParityReport> sourceParity(@PathVariable String id,
            @RequestParam(required = false) Double tolerance,
            @RequestParam(required = false) Double minCoverage,
            @RequestParam(required = false) Integer sampleSize) {
        return runGate(() -> parityService.parity(id, criteria(tolerance, minCoverage, sampleSize)));
    }

    @PostMapping("/{id}/migrate/native")
    @Operation(summary = "Flip this source to the native recipe — only when the parity gate passes")
    public ResponseEntity<DumParityReport> sourceMigrateNative(@PathVariable String id,
            @RequestParam(required = false) Double tolerance,
            @RequestParam(required = false) Double minCoverage,
            @RequestParam(required = false) Integer sampleSize) {
        return runGate(
                () -> parityService.migrateToNative(id, criteria(tolerance, minCoverage, sampleSize)));
    }

    @PostMapping("/{id}/migrate/sidecar")
    @Operation(summary = "Roll a source back to the sidecar (Python) leg")
    public ResponseEntity<DumCatalogSource> sourceMigrateSidecar(@PathVariable String id) {
        try {
            return ResponseEntity.ok(parityService.rollbackToSidecar(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private ResponseEntity<DumParityReport> runGate(java.util.function.Supplier<DumParityReport> gate) {
        try {
            return ResponseEntity.ok(gate.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private static DumParityCriteria criteria(Double tolerance, Double minCoverage,
            Integer sampleSize) {
        DumParityCriteria criteria = DumParityCriteria.defaults();
        if (tolerance != null) {
            criteria = criteria.withCountTolerance(tolerance);
        }
        if (minCoverage != null) {
            criteria = criteria.withMinUrlCoverage(minCoverage);
        }
        if (sampleSize != null) {
            criteria = criteria.withMaxSampleUrls(sampleSize);
        }
        return criteria;
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
