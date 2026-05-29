/*
 * Copyright (C) 2016-2024 the original author or authors.
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

import java.util.List;

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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.viglet.dumont.connector.plugin.eds.DumEDSPluginProcess;
import com.viglet.dumont.connector.plugin.eds.export.DumEDSExchangeProcess;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSExcludePatternRepository;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSFacetRepository;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSFieldMappingRepository;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSIndexRepository;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSRemoveSelectorRepository;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSSourceRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v2/eds/source")
@Tag(name = "EDS Source", description = "Adobe Edge Delivery Services Source")
public class DumEDSSourceApi {

    private final DumEDSSourceRepository dumEDSSourceRepository;
    private final DumEDSIndexRepository dumEDSIndexRepository;
    private final DumEDSExcludePatternRepository dumEDSExcludePatternRepository;
    private final DumEDSRemoveSelectorRepository dumEDSRemoveSelectorRepository;
    private final DumEDSFacetRepository dumEDSFacetRepository;
    private final DumEDSFieldMappingRepository dumEDSFieldMappingRepository;
    private final DumEDSPluginProcess dumEDSPluginProcess;
    private final DumEDSExchangeProcess dumEDSExchangeProcess;

    public DumEDSSourceApi(DumEDSSourceRepository dumEDSSourceRepository,
            DumEDSIndexRepository dumEDSIndexRepository,
            DumEDSExcludePatternRepository dumEDSExcludePatternRepository,
            DumEDSRemoveSelectorRepository dumEDSRemoveSelectorRepository,
            DumEDSFacetRepository dumEDSFacetRepository,
            DumEDSFieldMappingRepository dumEDSFieldMappingRepository,
            DumEDSPluginProcess dumEDSPluginProcess,
            DumEDSExchangeProcess dumEDSExchangeProcess) {
        this.dumEDSSourceRepository = dumEDSSourceRepository;
        this.dumEDSIndexRepository = dumEDSIndexRepository;
        this.dumEDSExcludePatternRepository = dumEDSExcludePatternRepository;
        this.dumEDSRemoveSelectorRepository = dumEDSRemoveSelectorRepository;
        this.dumEDSFacetRepository = dumEDSFacetRepository;
        this.dumEDSFieldMappingRepository = dumEDSFieldMappingRepository;
        this.dumEDSPluginProcess = dumEDSPluginProcess;
        this.dumEDSExchangeProcess = dumEDSExchangeProcess;
    }

    @GetMapping
    @Operation(summary = "List all EDS Sources")
    public List<DumEDSSource> list() {
        return dumEDSSourceRepository.findAll();
    }

    @GetMapping("/structure")
    @Operation(summary = "EDS Source structure")
    public DumEDSSource structure() {
        return new DumEDSSource();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Show an EDS Source")
    public DumEDSSource get(@PathVariable String id) {
        return dumEDSSourceRepository.findById(id).map(source -> {
            dumEDSIndexRepository.findByDumEDSSource(source).ifPresent(indexes -> {
                source.getIndexes().clear();
                source.getIndexes().addAll(indexes);
            });
            dumEDSExcludePatternRepository.findByDumEDSSource(source).ifPresent(patterns -> {
                source.getExcludePatterns().clear();
                source.getExcludePatterns().addAll(patterns);
            });
            dumEDSRemoveSelectorRepository.findByDumEDSSource(source).ifPresent(selectors -> {
                source.getRemoveSelectors().clear();
                source.getRemoveSelectors().addAll(selectors);
            });
            dumEDSFacetRepository.findByDumEDSSource(source).ifPresent(facets -> {
                source.getFacets().clear();
                source.getFacets().addAll(facets);
            });
            dumEDSFieldMappingRepository.findByDumEDSSource(source).ifPresent(mappings -> {
                source.getFieldMappings().clear();
                source.getFieldMappings().addAll(mappings);
            });
            return source;
        }).orElse(new DumEDSSource());
    }

    @Transactional
    @PutMapping("/{id}")
    @Operation(summary = "Update an EDS Source")
    public ResponseEntity<DumEDSSource> update(@PathVariable String id,
            @RequestBody DumEDSSource edsSource) {
        if (edsSource.getId() != null && !id.equals(edsSource.getId())) {
            return ResponseEntity.badRequest().build();
        }
        return dumEDSSourceRepository.findById(id).map(existing -> {
            existing.setTitle(edsSource.getTitle());
            existing.setDescription(edsSource.getDescription());
            existing.setBaseUrl(edsSource.getBaseUrl());
            existing.setLocale(edsSource.getLocale());
            existing.setLocaleClass(edsSource.getLocaleClass());
            existing.setSitemapFallback(edsSource.getSitemapFallback());
            existing.setBodySelector(edsSource.getBodySelector());
            existing.setUserAgent(edsSource.getUserAgent());
            existing.setFetchTimeoutMs(edsSource.getFetchTimeoutMs());
            existing.setRequestsPerSecond(edsSource.getRequestsPerSecond());
            existing.setMaxConcurrent(edsSource.getMaxConcurrent());
            existing.setTurSNSites(edsSource.getTurSNSites());
            existing.setFacetPathPattern(edsSource.getFacetPathPattern());
            existing.setIndexes(edsSource.getIndexes());
            existing.setExcludePatterns(edsSource.getExcludePatterns());
            existing.setRemoveSelectors(edsSource.getRemoveSelectors());
            existing.setFacets(edsSource.getFacets());
            existing.setFieldMappings(edsSource.getFieldMappings());

            if (edsSource.getIndexes() != null) {
                edsSource.getIndexes().forEach(idx -> idx.setDumEDSSource(existing));
            }
            if (edsSource.getExcludePatterns() != null) {
                edsSource.getExcludePatterns().forEach(p -> p.setDumEDSSource(existing));
            }
            if (edsSource.getRemoveSelectors() != null) {
                edsSource.getRemoveSelectors().forEach(s -> s.setDumEDSSource(existing));
            }
            if (edsSource.getFacets() != null) {
                edsSource.getFacets().forEach(f -> f.setDumEDSSource(existing));
            }
            if (edsSource.getFieldMappings() != null) {
                edsSource.getFieldMappings().forEach(m -> m.setDumEDSSource(existing));
            }
            return ResponseEntity.ok(dumEDSSourceRepository.save(existing));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Transactional
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an EDS Source")
    public boolean delete(@PathVariable String id) {
        return dumEDSSourceRepository.findById(id).map(source -> {
            dumEDSSourceRepository.delete(source);
            return true;
        }).orElse(false);
    }

    @PostMapping
    @Operation(summary = "Create an EDS Source")
    public DumEDSSource create(@RequestBody DumEDSSource edsSource) {
        if (edsSource.getIndexes() != null) {
            edsSource.getIndexes().forEach(idx -> idx.setDumEDSSource(edsSource));
        }
        if (edsSource.getExcludePatterns() != null) {
            edsSource.getExcludePatterns().forEach(p -> p.setDumEDSSource(edsSource));
        }
        if (edsSource.getRemoveSelectors() != null) {
            edsSource.getRemoveSelectors().forEach(s -> s.setDumEDSSource(edsSource));
        }
        if (edsSource.getFacets() != null) {
            edsSource.getFacets().forEach(f -> f.setDumEDSSource(edsSource));
        }
        if (edsSource.getFieldMappings() != null) {
            edsSource.getFieldMappings().forEach(m -> m.setDumEDSSource(edsSource));
        }
        return dumEDSSourceRepository.save(edsSource);
    }

    @GetMapping("/{id}/index")
    @Operation(summary = "Trigger EDS discovery + indexing for a source")
    public ResponseEntity<Object> index(@PathVariable String id) {
        dumEDSSourceRepository.findById(id).ifPresent(dumEDSPluginProcess::start);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/export")
    @Operation(summary = "Export all EDS Sources as a ZIP containing export.json")
    public StreamingResponseBody export(HttpServletResponse response) {
        return dumEDSExchangeProcess.exportObject(response);
    }

    @PostMapping("/import")
    @Operation(summary = "Import EDS Sources from a ZIP previously produced by /export")
    public ResponseEntity<Object> importSources(@RequestParam("file") MultipartFile file) {
        dumEDSExchangeProcess.importFromMultipartFile(file);
        return ResponseEntity.ok().build();
    }
}
