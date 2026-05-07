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
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher events;

    public DumPluginManagementService(
            PluginManager pluginManager,
            ApplicationEventPublisher events,
            @Value("${dumont.plugins.directory:./plugins}") String pluginsDirectory) {
        this.pluginManager = pluginManager;
        this.events = events;
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
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Only .jar files are accepted");
        }
        String safeName = Paths.get(filename).getFileName().toString();
        Path target = pluginsPath.resolve(safeName).normalize();
        if (!target.startsWith(pluginsPath)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Invalid filename");
        }

        // On Windows, the JAR is locked by the PluginClassLoader while the plugin
        // is loaded. Unload any plugin sitting on the same filename so we can
        // overwrite it transparently (acts as an in-place upgrade).
        unloadPluginAtFilename(safeName);

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
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Failed to load plugin: " + e.getMessage(), e);
        }
        if (pluginId == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Plugin loaded but no plugin id was resolved");
        }
        try {
            pluginManager.startPlugin(pluginId);
        } catch (RuntimeException e) {
            log.warn("Plugin {} loaded but failed to start: {}", pluginId, e.getMessage());
        }
        log.info("Plugin uploaded and loaded: {} ({})", pluginId, safeName);
        events.publishEvent(new PluginsChangedEvent(pluginId, "upload"));
        return findOrThrow(pluginId);
    }

    private void unloadPluginAtFilename(String filename) {
        for (PluginWrapper pw : pluginManager.getPlugins()) {
            Path path = pw.getPluginPath();
            if (path == null || path.getFileName() == null) {
                continue;
            }
            if (path.getFileName().toString().equalsIgnoreCase(filename)) {
                String id = pw.getPluginId();
                log.info("Unloading existing plugin {} ({}) for replacement", id, filename);
                pluginManager.unloadPlugin(id);
                return;
            }
        }
    }

    public synchronized DumPluginInfo start(String id) {
        requireExists(id);
        PluginState state = pluginManager.startPlugin(id);
        log.info("Plugin {} started -> {}", id, state);
        events.publishEvent(new PluginsChangedEvent(id, "start"));
        return findOrThrow(id);
    }

    public synchronized DumPluginInfo stop(String id) {
        requireExists(id);
        PluginState state = pluginManager.stopPlugin(id);
        log.info("Plugin {} stopped -> {}", id, state);
        events.publishEvent(new PluginsChangedEvent(id, "stop"));
        return findOrThrow(id);
    }

    public synchronized DumPluginInfo disable(String id) {
        requireExists(id);
        if (!pluginManager.disablePlugin(id)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Failed to disable plugin " + id);
        }
        events.publishEvent(new PluginsChangedEvent(id, "disable"));
        return findOrThrow(id);
    }

    public synchronized DumPluginInfo enable(String id) {
        requireExists(id);
        if (!pluginManager.enablePlugin(id)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Failed to enable plugin " + id);
        }
        events.publishEvent(new PluginsChangedEvent(id, "enable"));
        return findOrThrow(id);
    }

    public synchronized void delete(String id) {
        requireExists(id);
        if (!pluginManager.deletePlugin(id)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Failed to delete plugin " + id);
        }
        log.info("Plugin {} deleted", id);
        events.publishEvent(new PluginsChangedEvent(id, "delete"));
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
