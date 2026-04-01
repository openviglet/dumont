/*
 * Copyright (C) 2016-2024 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.viglet.dumont.connector.plugin.webcrawler.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.viglet.dumont.connector.plugin.webcrawler.DumWCPluginProcess;
import com.viglet.dumont.connector.plugin.webcrawler.export.DumWCExchangeProcess;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.*;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v2/wc/source")
@Tag(name = "Web Crawler Source", description = "Web Crawler Source")
public class DumWCSourceApi {

    private final DumWCSourceRepository dumWCSourceRepository;
    private final DumWCStartingPointRepository dumWCStartingPointRepository;
    private final DumWCAllowUrlRepository dumWCAllowUrlRepository;
    private final DumWCNotAllowUrlRepository dumWCNotAllowUrlRepository;
    private final DumWCFileExtensionRepository dumWCFileExtensionRepository;
    private final DumWCAttributeMappingRepository dumWCAttributeMappingRepository;
    private final DumWCPluginProcess dumWCPluginProcess;
    private final DumWCExchangeProcess dumWCExchangeProcess;

    public DumWCSourceApi(DumWCSourceRepository dumWCSourceRepository,
            DumWCStartingPointRepository dumWCStartingPointRepository,
            DumWCAllowUrlRepository dumWCAllowUrlRepository,
            DumWCNotAllowUrlRepository dumWCNotAllowUrlRepository,
            DumWCFileExtensionRepository dumWCFileExtensionRepository,
            DumWCAttributeMappingRepository dumWCAttributeMappingRepository,
            DumWCPluginProcess dumWCPluginProcess,
            DumWCExchangeProcess dumWCExchangeProcess) {
        this.dumWCSourceRepository = dumWCSourceRepository;
        this.dumWCStartingPointRepository = dumWCStartingPointRepository;
        this.dumWCAllowUrlRepository = dumWCAllowUrlRepository;
        this.dumWCNotAllowUrlRepository = dumWCNotAllowUrlRepository;
        this.dumWCFileExtensionRepository = dumWCFileExtensionRepository;
        this.dumWCAttributeMappingRepository = dumWCAttributeMappingRepository;
        this.dumWCPluginProcess = dumWCPluginProcess;
        this.dumWCExchangeProcess = dumWCExchangeProcess;
    }

    @GetMapping
    @Operation(summary = "List all WC Sources")
    public List<DumWCSource> list() {
        return dumWCSourceRepository.findAll();
    }

    @GetMapping("/structure")
    @Operation(summary = "WC Source structure")
    public DumWCSource structure() {
        return new DumWCSource();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Show a WC Source")
    public DumWCSource get(@PathVariable String id) {
        return dumWCSourceRepository.findById(id).map(source -> {
            dumWCStartingPointRepository.findByDumWCSource(source)
                    .ifPresent(sp -> { source.getStartingPoints().clear(); source.getStartingPoints().addAll(sp); });
            dumWCAllowUrlRepository.findByDumWCSource(source)
                    .ifPresent(au -> { source.getAllowUrls().clear(); source.getAllowUrls().addAll(au); });
            dumWCNotAllowUrlRepository.findByDumWCSource(source)
                    .ifPresent(nu -> { source.getNotAllowUrls().clear(); source.getNotAllowUrls().addAll(nu); });
            dumWCFileExtensionRepository.findByDumWCSource(source)
                    .ifPresent(fe -> { source.getNotAllowExtensions().clear(); source.getNotAllowExtensions().addAll(fe); });
            dumWCAttributeMappingRepository.findByDumWCSource(source)
                    .ifPresent(am -> { source.getAttributeMappings().clear(); source.getAttributeMappings().addAll(am); });
            return source;
        }).orElse(new DumWCSource());
    }

    @Transactional
    @PutMapping("/{id}")
    @Operation(summary = "Update a WC Source")
    public ResponseEntity<DumWCSource> update(@PathVariable String id, @RequestBody DumWCSource wcSource) {
        if (wcSource.getId() != null && !id.equals(wcSource.getId())) {
            return ResponseEntity.badRequest().build();
        }
        return dumWCSourceRepository.findById(id).map(existing -> {
            existing.setTitle(wcSource.getTitle());
            existing.setDescription(wcSource.getDescription());
            existing.setUrl(wcSource.getUrl());
            existing.setLocale(wcSource.getLocale());
            existing.setLocaleClass(wcSource.getLocaleClass());
            existing.setUsername(wcSource.getUsername());
            existing.setPassword(wcSource.getPassword());
            existing.setTurSNSites(wcSource.getTurSNSites());
            existing.setStartingPoints(wcSource.getStartingPoints());
            existing.setAllowUrls(wcSource.getAllowUrls());
            existing.setNotAllowUrls(wcSource.getNotAllowUrls());
            existing.setNotAllowExtensions(wcSource.getNotAllowExtensions());
            existing.setAttributeMappings(wcSource.getAttributeMappings());

            if (wcSource.getStartingPoints() != null)
                wcSource.getStartingPoints().forEach(sp -> sp.setDumWCSource(existing));
            if (wcSource.getAllowUrls() != null)
                wcSource.getAllowUrls().forEach(au -> au.setDumWCSource(existing));
            if (wcSource.getNotAllowUrls() != null)
                wcSource.getNotAllowUrls().forEach(nu -> nu.setDumWCSource(existing));
            if (wcSource.getNotAllowExtensions() != null)
                wcSource.getNotAllowExtensions().forEach(fe -> fe.setDumWCSource(existing));
            if (wcSource.getAttributeMappings() != null)
                wcSource.getAttributeMappings().forEach(am -> am.setDumWCSource(existing));

            return ResponseEntity.ok(dumWCSourceRepository.save(existing));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Transactional
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a WC Source")
    public boolean delete(@PathVariable String id) {
        return dumWCSourceRepository.findById(id).map(source -> {
            dumWCSourceRepository.delete(source);
            return true;
        }).orElse(false);
    }

    @PostMapping
    @Operation(summary = "Create a WC Source")
    public DumWCSource create(@RequestBody DumWCSource wcSource) {
        if (wcSource.getStartingPoints() != null)
            wcSource.getStartingPoints().forEach(sp -> sp.setDumWCSource(wcSource));
        if (wcSource.getAllowUrls() != null)
            wcSource.getAllowUrls().forEach(au -> au.setDumWCSource(wcSource));
        if (wcSource.getNotAllowUrls() != null)
            wcSource.getNotAllowUrls().forEach(nu -> nu.setDumWCSource(wcSource));
        if (wcSource.getNotAllowExtensions() != null)
            wcSource.getNotAllowExtensions().forEach(fe -> fe.setDumWCSource(wcSource));
        if (wcSource.getAttributeMappings() != null)
            wcSource.getAttributeMappings().forEach(am -> am.setDumWCSource(wcSource));
        return dumWCSourceRepository.save(wcSource);
    }

    @GetMapping("/{id}/crawl")
    @Operation(summary = "Start crawling a WC Source")
    public ResponseEntity<Object> crawl(@PathVariable String id) {
        dumWCSourceRepository.findById(id).ifPresent(dumWCPluginProcess::start);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/export")
    @Operation(summary = "Export all WC Sources")
    public StreamingResponseBody export(HttpServletResponse response) {
        return dumWCExchangeProcess.exportObject(response);
    }

    @PostMapping("/import")
    @Operation(summary = "Import WC Sources from ZIP")
    public ResponseEntity<Object> importSources(@RequestParam("file") MultipartFile file) {
        dumWCExchangeProcess.importFromMultipartFile(file);
        return ResponseEntity.ok().build();
    }
}
