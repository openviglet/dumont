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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class DumPluginConfiguration {

    private final Path pluginsPath;
    private PluginManager pluginManager;

    public DumPluginConfiguration(@Value("${dumont.plugins.directory:./plugins}") String pluginsDirectory) {
        this.pluginsPath = Paths.get(pluginsDirectory).toAbsolutePath().normalize();
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
        pluginManager.loadPlugins();
        pluginManager.startPlugins();

        List<PluginWrapper> plugins = pluginManager.getPlugins();
        if (plugins.isEmpty()) {
            log.info("PF4J: no plugins found in {}", pluginsPath);
            return;
        }
        log.info("PF4J: loaded {} plugin(s) from {}", plugins.size(), pluginsPath);
        plugins.forEach(plugin -> log.info("  - {} v{} [{}]",
                plugin.getPluginId(),
                plugin.getDescriptor().getVersion(),
                plugin.getPluginState()));
    }

    @PreDestroy
    public void onShutdown() {
        if (pluginManager != null) {
            pluginManager.stopPlugins();
            pluginManager.unloadPlugins();
        }
    }
}
