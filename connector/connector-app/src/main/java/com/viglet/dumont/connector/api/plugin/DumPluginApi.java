/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */

package com.viglet.dumont.connector.api.plugin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v2/plugins")
@Tag(name = "Plugin API", description = "PF4J plugin management")
public class DumPluginApi {

    private final DumPluginManagementService service;

    public DumPluginApi(DumPluginManagementService service) {
        this.service = service;
    }

    @GetMapping
    public List<DumPluginInfo> list() {
        return service.list();
    }

    @PostMapping(consumes = "multipart/form-data")
    public DumPluginInfo upload(@RequestPart("file") MultipartFile file) {
        return service.upload(file);
    }

    @PostMapping("{id}/start")
    public DumPluginInfo start(@PathVariable String id) {
        return service.start(id);
    }

    @PostMapping("{id}/stop")
    public DumPluginInfo stop(@PathVariable String id) {
        return service.stop(id);
    }

    @PostMapping("{id}/disable")
    public DumPluginInfo disable(@PathVariable String id) {
        return service.disable(id);
    }

    @PostMapping("{id}/enable")
    public DumPluginInfo enable(@PathVariable String id) {
        return service.enable(id);
    }

    @DeleteMapping("{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("file/{filename}")
    public ResponseEntity<Void> deleteFailed(@PathVariable String filename) {
        service.deleteFailed(filename);
        return ResponseEntity.noContent().build();
    }
}
