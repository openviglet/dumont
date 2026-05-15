/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */

package com.viglet.dumont.connector.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import com.viglet.dumont.connector.api.plugin.DumFailedPluginRegistry;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class DumPluginConfiguration {

    private final Path pluginsPath;
    private final DumFailedPluginRegistry failedRegistry;
    private PluginManager pluginManager;

    public DumPluginConfiguration(
            @Value("${dumont.plugins.directory:./plugins}") String pluginsDirectory,
            DumFailedPluginRegistry failedRegistry) {
        this.pluginsPath = Paths.get(pluginsDirectory).toAbsolutePath().normalize();
        this.failedRegistry = failedRegistry;
    }

    @Bean
    PluginManager pluginManager() throws Exception {
        if (!Files.exists(pluginsPath)) {
            Files.createDirectories(pluginsPath);
            log.info("Created plugins directory at {}", pluginsPath);
        }
        this.pluginManager = new DefaultPluginManager(pluginsPath);
        return this.pluginManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadPluginsOnStartup() {
        if (pluginManager == null) {
            return;
        }
        failedRegistry.clear();
        loadEachJarIndividually();
        pluginManager.startPlugins();

        List<PluginWrapper> plugins = pluginManager.getPlugins();
        if (plugins.isEmpty() && failedRegistry.list().isEmpty()) {
            log.info("PF4J: no plugins found in {}", pluginsPath);
            return;
        }
        log.info("PF4J: loaded {} plugin(s) from {} ({} failed)",
                plugins.size(), pluginsPath, failedRegistry.list().size());
        plugins.forEach(plugin -> log.info("  - {} v{} [{}]",
                plugin.getPluginId(),
                plugin.getDescriptor().getVersion(),
                plugin.getPluginState()));
    }

    private void loadEachJarIndividually() {
        try (Stream<Path> stream = Files.list(pluginsPath)) {
            stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar"))
                    .sorted()
                    .forEach(this::tryLoad);
        } catch (IOException e) {
            log.error("Failed to scan plugins directory {}: {}", pluginsPath, e.getMessage());
        }
    }

    private void tryLoad(Path jar) {
        try {
            pluginManager.loadPlugin(jar);
        } catch (RuntimeException e) {
            failedRegistry.register(jar, rootCauseMessage(e));
        }
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String msg = current.getMessage();
        return msg != null ? msg : current.getClass().getSimpleName();
    }

    @PreDestroy
    public void onShutdown() {
        if (pluginManager != null) {
            pluginManager.stopPlugins();
            pluginManager.unloadPlugins();
        }
    }
}
