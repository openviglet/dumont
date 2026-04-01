/*
 * Copyright (C) 2016-2024 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.db.api;

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

import com.viglet.dumont.connector.plugin.db.DumDbPluginProcess;
import com.viglet.dumont.connector.plugin.db.export.DumDbExchangeProcess;
import com.viglet.dumont.connector.plugin.db.persistence.model.DumDbSource;
import com.viglet.dumont.connector.plugin.db.persistence.repository.DumDbSourceRepository;

import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v2/db/source")
@Tag(name = "DB Source", description = "Database Source")
public class DumDbSourceApi {

    private final DumDbSourceRepository dumDbSourceRepository;
    private final DumDbPluginProcess dumDbPluginProcess;
    private final DumDbExchangeProcess dumDbExchangeProcess;

    public DumDbSourceApi(DumDbSourceRepository dumDbSourceRepository,
            DumDbPluginProcess dumDbPluginProcess,
            DumDbExchangeProcess dumDbExchangeProcess) {
        this.dumDbSourceRepository = dumDbSourceRepository;
        this.dumDbPluginProcess = dumDbPluginProcess;
        this.dumDbExchangeProcess = dumDbExchangeProcess;
    }

    @GetMapping
    @Operation(summary = "List all DB Sources")
    public List<DumDbSource> dumDbSourceList() {
        return dumDbSourceRepository.findAll();
    }

    @GetMapping("/structure")
    @Operation(summary = "DB Source structure")
    public DumDbSource dumDbSourceStructure() {
        DumDbSource source = new DumDbSource();
        source.setLocale("en_US");
        source.setContentType("CONTENT_TYPE");
        source.setChunk(100);
        source.setMaxContentMegaByteSize(5);
        source.setEncoding("UTF-8");
        source.setMultiValuedSeparator(",");
        return source;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Show a DB Source")
    public DumDbSource dumDbSourceGet(@PathVariable String id) {
        return dumDbSourceRepository.findById(id).orElse(new DumDbSource());
    }

    @Transactional
    @PutMapping("/{id}")
    @Operation(summary = "Update a DB Source")
    public ResponseEntity<DumDbSource> dumDbSourceUpdate(@PathVariable String id,
            @RequestBody DumDbSource dumDbSource) {
        if (dumDbSource.getId() != null && !id.equals(dumDbSource.getId())) {
            return ResponseEntity.badRequest().build();
        }

        return dumDbSourceRepository.findById(id).map(existing -> {
            existing.setName(dumDbSource.getName());
            existing.setDescription(dumDbSource.getDescription());
            existing.setDriver(dumDbSource.getDriver());
            existing.setUrl(dumDbSource.getUrl());
            existing.setDbUsername(dumDbSource.getDbUsername());
            existing.setDbPassword(dumDbSource.getDbPassword());
            existing.setQuery(dumDbSource.getQuery());
            existing.setSite(dumDbSource.getSite());
            existing.setLocale(dumDbSource.getLocale());
            existing.setContentType(dumDbSource.getContentType());
            existing.setChunk(dumDbSource.getChunk());
            existing.setTypeInId(dumDbSource.isTypeInId());
            existing.setMultiValuedSeparator(dumDbSource.getMultiValuedSeparator());
            existing.setMultiValuedFields(dumDbSource.getMultiValuedFields());
            existing.setRemoveHtmlTagsFields(dumDbSource.getRemoveHtmlTagsFields());
            existing.setFilePathField(dumDbSource.getFilePathField());
            existing.setFileContentField(dumDbSource.getFileContentField());
            existing.setFileSizeField(dumDbSource.getFileSizeField());
            existing.setCustomClassName(dumDbSource.getCustomClassName());
            existing.setMaxContentMegaByteSize(dumDbSource.getMaxContentMegaByteSize());
            existing.setEncoding(dumDbSource.getEncoding());
            existing.setShowOutput(dumDbSource.isShowOutput());
            existing.setDeindexBeforeImporting(dumDbSource.isDeindexBeforeImporting());

            existing.getTurSNSites().clear();
            if (dumDbSource.getTurSNSites() != null) {
                existing.getTurSNSites().addAll(dumDbSource.getTurSNSites());
            }

            DumDbSource saved = dumDbSourceRepository.save(existing);
            return ResponseEntity.ok(saved);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Transactional
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a DB Source")
    public boolean dumDbSourceDelete(@PathVariable String id) {
        return dumDbSourceRepository.findById(id).map(source -> {
            dumDbSourceRepository.delete(source);
            return true;
        }).orElse(false);
    }

    @PostMapping
    @Operation(summary = "Create a DB Source")
    public DumDbSource dumDbSourceAdd(@RequestBody DumDbSource dumDbSource) {
        return dumDbSourceRepository.save(dumDbSource);
    }

    @GetMapping("/{id}/indexAll")
    @Operation(summary = "Index all content from a DB Source")
    public ResponseEntity<Object> sourceIndexAll(@PathVariable String id) {
        dumDbPluginProcess.indexAllByIdAsync(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/export")
    @Operation(summary = "Export all DB Sources")
    public StreamingResponseBody exportSources(HttpServletResponse response) {
        return dumDbExchangeProcess.exportObject(response);
    }

    @PostMapping("/import")
    @Operation(summary = "Import DB Sources from ZIP")
    public ResponseEntity<Object> importSources(@RequestParam("file") MultipartFile file) {
        dumDbExchangeProcess.importFromMultipartFile(file);
        return ResponseEntity.ok().build();
    }
}
