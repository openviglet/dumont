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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

import org.pf4j.PluginManager;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DumPluginManagementService {

    private final PluginManager pluginManager;
    private final Path pluginsPath;

    public DumPluginManagementService(
            PluginManager pluginManager,
            @Value("${dumont.plugins.directory:./plugins}") String pluginsDirectory) {
        this.pluginManager = pluginManager;
        this.pluginsPath = Paths.get(pluginsDirectory).toAbsolutePath().normalize();
    }

    public List<DumPluginInfo> list() {
        return pluginManager.getPlugins().stream()
                .map(DumPluginInfo::from)
                .toList();
    }

    public synchronized DumPluginInfo upload(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".jar")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only .jar files are accepted");
        }
        String safeName = Paths.get(filename).getFileName().toString();
        Path target = pluginsPath.resolve(safeName).normalize();
        if (!target.startsWith(pluginsPath)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Invalid filename");
        }
        try {
            Files.createDirectories(pluginsPath);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to save plugin: " + e.getMessage(), e);
        }
        String pluginId;
        try {
            pluginId = pluginManager.loadPlugin(target);
        } catch (RuntimeException e) {
            log.warn("Failed to load plugin {}: {}", safeName, e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Failed to load plugin: " + e.getMessage(), e);
        }
        if (pluginId == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Plugin loaded but no plugin id was resolved");
        }
        log.info("Plugin uploaded and loaded: {} ({})", pluginId, safeName);
        return findOrThrow(pluginId);
    }

    public synchronized DumPluginInfo start(String id) {
        requireExists(id);
        PluginState state = pluginManager.startPlugin(id);
        log.info("Plugin {} started -> {}", id, state);
        return findOrThrow(id);
    }

    public synchronized DumPluginInfo stop(String id) {
        requireExists(id);
        PluginState state = pluginManager.stopPlugin(id);
        log.info("Plugin {} stopped -> {}", id, state);
        return findOrThrow(id);
    }

    public synchronized DumPluginInfo disable(String id) {
        requireExists(id);
        if (!pluginManager.disablePlugin(id)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Failed to disable plugin " + id);
        }
        return findOrThrow(id);
    }

    public synchronized DumPluginInfo enable(String id) {
        requireExists(id);
        if (!pluginManager.enablePlugin(id)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Failed to enable plugin " + id);
        }
        return findOrThrow(id);
    }

    public synchronized void delete(String id) {
        requireExists(id);
        if (!pluginManager.deletePlugin(id)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Failed to delete plugin " + id);
        }
        log.info("Plugin {} deleted", id);
    }

    private void requireExists(String id) {
        if (pluginManager.getPlugin(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plugin not found: " + id);
        }
    }

    private DumPluginInfo findOrThrow(String id) {
        return Optional.ofNullable(pluginManager.getPlugin(id))
                .map(DumPluginInfo::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Plugin not found: " + id));
    }

    PluginWrapper getRaw(String id) {
        return pluginManager.getPlugin(id);
    }
}
