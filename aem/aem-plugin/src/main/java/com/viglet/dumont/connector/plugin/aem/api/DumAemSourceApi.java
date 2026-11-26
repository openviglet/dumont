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

import com.viglet.dumont.connector.commons.exchange.DumSourceImportMode;
import com.viglet.dumont.connector.commons.exchange.DumSourceImportException;
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

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.viglet.dumont.connector.commons.DumSourceNames;
import com.viglet.dumont.connector.plugin.aem.DumAemPluginProcess;
import com.viglet.dumont.connector.plugin.aem.export.DumAemExchangeProcess;
import com.viglet.dumont.connector.plugin.aem.mapper.DumAemSourceMapper;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemPluginModel;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemAttributeSpecificationRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemPluginModelRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemSourceLocalePathRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemSourceRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v2/aem/source")
@Tag(name = "AEM Source", description = "AEM Source")
public class DumAemSourceApi {

    private final DumAemSourceRepository dumAemSourceRepository;
    private final DumAemSourceLocalePathRepository dumAemSourceLocalePathRepository;
    private final DumAemAttributeSpecificationRepository dumAemAttributeSpecificationRepository;
    private final DumAemPluginModelRepository dumAemPluginModelRepository;
    private final DumAemPluginProcess dumAemPluginProcess;
    private final DumAemSourceMapper dumAemSourceMapper;
    private final DumAemExchangeProcess dumAemExchangeProcess;

    public DumAemSourceApi(DumAemSourceRepository dumAemSourceRepository,
            DumAemSourceLocalePathRepository dumAemSourceLocalePathRepository,
            DumAemAttributeSpecificationRepository dumAemAttributeSpecificationRepository,
            DumAemPluginModelRepository dumAemPluginModelRepository,
            DumAemPluginProcess dumAemPluginProcess,
            DumAemSourceMapper dumAemSourceMapper,
            DumAemExchangeProcess dumAemExchangeProcess) {
        this.dumAemSourceRepository = dumAemSourceRepository;
        this.dumAemSourceLocalePathRepository = dumAemSourceLocalePathRepository;
        this.dumAemAttributeSpecificationRepository = dumAemAttributeSpecificationRepository;
        this.dumAemPluginModelRepository = dumAemPluginModelRepository;
        this.dumAemPluginProcess = dumAemPluginProcess;
        this.dumAemSourceMapper = dumAemSourceMapper;
        this.dumAemExchangeProcess = dumAemExchangeProcess;
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
            dumAemAttributeSpecificationRepository.findByDumAemSource(dumAemSource)
                    .ifPresent(specs -> {
                        dumAemSource.getAttributeSpecifications().clear();
                        dumAemSource.getAttributeSpecifications().addAll(specs);
                    });
            var models = dumAemPluginModelRepository.findByDumAemSource(dumAemSource);
            if (!models.isEmpty()) {
                dumAemSource.getModels().clear();
                dumAemSource.getModels().addAll(models);
            }
            return dumAemSource;
        }).orElse(new DumAemSource());
    }

    @Transactional
    @Operation(summary = "Update a AEM Source")
    @PutMapping("/{id}")
    public ResponseEntity<DumAemSource> dumAemSourceUpdate(@PathVariable String id,
            @RequestBody DumAemSource dumAemSource) {
        if (dumAemSource.getId() != null && !id.equals(dumAemSource.getId())) {
            return ResponseEntity.badRequest().build();
        }
        if (!DumSourceNames.isValid(dumAemSource.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, DumSourceNames.RULE);
        }

        return dumAemSourceRepository.findById(id).map(dumAemSourceEdit -> {
            dumAemSourceMapper.update(dumAemSourceEdit, dumAemSource);

            // Update localePaths collection in-place to respect orphanRemoval
            dumAemSourceEdit.getLocalePaths().clear();
            if (dumAemSource.getLocalePaths() != null) {
                dumAemSource.getLocalePaths().forEach(localePath -> {
                    localePath.setDumAemSource(dumAemSourceEdit);
                    dumAemSourceEdit.getLocalePaths().add(localePath);
                });
            }

            // Update attributeSpecifications collection in-place to respect orphanRemoval
            dumAemSourceEdit.getAttributeSpecifications().clear();
            if (dumAemSource.getAttributeSpecifications() != null) {
                dumAemSource.getAttributeSpecifications().forEach(spec -> {
                    spec.setDumAemSource(dumAemSourceEdit);
                    dumAemSourceEdit.getAttributeSpecifications().add(spec);
                });
            }

            // Update models collection in-place to respect orphanRemoval
            dumAemSourceEdit.getModels().clear();
            if (dumAemSource.getModels() != null) {
                dumAemSource.getModels().forEach(model -> {
                    wireModelHierarchy(model, dumAemSourceEdit);
                    dumAemSourceEdit.getModels().add(model);
                });
            }

            DumAemSource saved = this.dumAemSourceRepository.save(dumAemSourceEdit);
            return ResponseEntity.ok(saved);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static void wireModelHierarchy(DumAemPluginModel model, DumAemSource source) {
        model.setDumAemSource(source);
        if (model.getTargetAttrs() == null) {
            return;
        }
        model.getTargetAttrs().forEach(targetAttr -> {
            targetAttr.setDumAemModel(model);
            if (targetAttr.getSourceAttrs() != null) {
                targetAttr.getSourceAttrs().forEach(
                        sourceAttr -> sourceAttr.setDumAemTargetAttribute(targetAttr));
            }
        });
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
        if (!DumSourceNames.isValid(dumAemSource.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, DumSourceNames.RULE);
        }
        dumAemSource.getLocalePaths().forEach(localePath -> localePath.setDumAemSource(dumAemSource));
        dumAemSource.getAttributeSpecifications().forEach(spec -> spec.setDumAemSource(dumAemSource));
        dumAemSource.getModels().forEach(model -> wireModelHierarchy(model, dumAemSource));
        this.dumAemSourceRepository.save(dumAemSource);
        return dumAemSource;
    }

    @GetMapping("{id}/indexAll")
    public ResponseEntity<Object> sourceIndexAll(@PathVariable String id) {
        dumAemPluginProcess.indexAllByIdAsync(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Every AEM source as a zip holding {@code export.json} (roadmap <strong>DU2</strong>): the same
     * file the first-start import reads from {@code export/}, so it seeds another instance unchanged.
     */
    @GetMapping("/export")
    @Operation(summary = "Export all AEM Sources")
    public StreamingResponseBody exportSources(HttpServletResponse response) {
        return dumAemExchangeProcess.exportObject(response);
    }

    @PostMapping("/import")
    @Operation(summary = "Import AEM Sources from ZIP")
    /**
     * Imports a zip or plain {@code .json} export. {@code mode=add} (the default) skips sources that
     * exist; {@code mode=replace} makes the file the whole set (roadmap DU9). A refused file answers
     * 400 with the reason, and nothing is changed.
     */
    public ResponseEntity<Object> importSources(@RequestParam("file") MultipartFile file,
            @RequestParam(name = "mode", required = false) String mode) {
        try {
            dumAemExchangeProcess.importFromMultipartFile(file, DumSourceImportMode.of(mode));
        } catch (DumSourceImportException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }
}
