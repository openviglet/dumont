/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.aem.api;

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
import org.springframework.web.bind.annotation.RestController;

import com.viglet.dumont.connector.plugin.aem.DumAemPluginProcess;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemSourceLocalePathRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemSourceRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v2/aem/source")
@Tag(name = "AEM Source", description = "AEM Source")
public class DumAemSourceApi {

    private final DumAemSourceRepository dumAemSourceRepository;
    private final DumAemSourceLocalePathRepository dumAemSourceLocalePathRepository;
    private final DumAemPluginProcess dumAemPluginProcess;

    public DumAemSourceApi(DumAemSourceRepository dumAemSourceRepository,
            DumAemSourceLocalePathRepository dumAemSourceLocalePathRepository,
            DumAemPluginProcess dumAemPluginProcess) {
        this.dumAemSourceRepository = dumAemSourceRepository;
        this.dumAemSourceLocalePathRepository = dumAemSourceLocalePathRepository;
        this.dumAemPluginProcess = dumAemPluginProcess;
    }

    @GetMapping
    public List<DumAemSource> dumAemSourceList() {
        return dumAemSourceRepository.findAll();
    }

    @Operation(summary = "AEM Source structure")
    @GetMapping("/structure")
    public DumAemSource dumAemSourceStructure() {
        return new DumAemSource();

    }

    @Operation(summary = "Show a AEM Source")
    @GetMapping("/{id}")
    public DumAemSource dumAemSourceGet(@PathVariable String id) {
        return this.dumAemSourceRepository.findById(id).map(dumAemSource -> {
            dumAemSourceLocalePathRepository.findByDumAemSource(dumAemSource)
                    .ifPresent(localePaths -> {
                        dumAemSource.getLocalePaths().clear();
                        dumAemSource.getLocalePaths().addAll(localePaths);
                    });
            return dumAemSource;
        }).orElse(new DumAemSource());
    }

    @Operation(summary = "Update a AEM Source")
    @PutMapping("/{id}")
    public DumAemSource dumAemSourceUpdate(@PathVariable String id, @RequestBody DumAemSource dumAemSource) {
        return dumAemSourceRepository.findById(id).map(dumAemSourceEdit -> {
            dumAemSourceEdit.setName(dumAemSource.getName());
            dumAemSourceEdit.setEndpoint(dumAemSource.getEndpoint());
            dumAemSourceEdit.setUsername(dumAemSource.getUsername());
            dumAemSourceEdit.setPassword(dumAemSource.getPassword());
            dumAemSourceEdit.setAuthorURLPrefix(dumAemSource.getAuthorURLPrefix());
            dumAemSourceEdit.setPublishURLPrefix(dumAemSource.getPublishURLPrefix());
            dumAemSourceEdit.setAuthor(dumAemSource.isAuthor());
            dumAemSourceEdit.setPublish(dumAemSource.isPublish());
            dumAemSourceEdit.setOncePattern(dumAemSource.getOncePattern());
            dumAemSourceEdit.setRootPath(dumAemSource.getRootPath());

            // Update localePaths collection in-place to respect orphanRemoval
            dumAemSourceEdit.getLocalePaths().clear();
            dumAemSource.getLocalePaths().forEach(localePath -> {
                localePath.setDumAemSource(dumAemSourceEdit);
                dumAemSourceEdit.getLocalePaths().add(localePath);
            });

            this.dumAemSourceRepository.save(dumAemSourceEdit);
            return dumAemSourceEdit;
        }).orElse(new DumAemSource());

    }

    @Transactional
    @Operation(summary = "Delete a AEM Source")
    @DeleteMapping("/{id}")
    public boolean dumAemSourceDelete(@PathVariable String id) {
        return dumAemSourceRepository.findById(id).map(dumAemSource -> {
            dumAemSourceRepository.delete(dumAemSource);
            return true;
        }).orElse(false);
    }

    @Operation(summary = "Create a AEM Source")
    @PostMapping
    public DumAemSource dumAemSourceAdd(@RequestBody DumAemSource dumAemSource) {
        this.dumAemSourceRepository.save(dumAemSource);
        return dumAemSource;
    }

    @GetMapping("{id}/indexAll")
    public ResponseEntity<Object> sourceIndexAll(@PathVariable String id) {
        dumAemPluginProcess.indexAllByIdAsync(id);
        return ResponseEntity.ok().build();
    }
}
