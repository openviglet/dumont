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

import java.util.Optional;

import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.stereotype.Component;

import com.viglet.dumont.commons.cache.DumCustomClassCache;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DumPluginClassResolver {

    private final PluginManager pluginManager;

    public DumPluginClassResolver(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    @PostConstruct
    public void register() {
        DumCustomClassCache.setExternalResolver(this::resolve);
        log.info("DumCustomClassCache external resolver registered (PF4J)");
    }

    private Optional<Object> resolve(String fqcn) {
        for (PluginWrapper plugin : pluginManager.getStartedPlugins()) {
            ClassLoader cl = plugin.getPluginClassLoader();
            try {
                Class<?> clazz = Class.forName(fqcn, true, cl);
                Object instance = clazz.getDeclaredConstructor().newInstance();
                log.debug("Resolved {} from plugin {}", fqcn, plugin.getPluginId());
                return Optional.of(instance);
            } catch (ClassNotFoundException ignored) {
                // try next plugin
            } catch (ReflectiveOperationException e) {
                log.warn("Failed to instantiate {} from plugin {}: {}",
                        fqcn, plugin.getPluginId(), e.getMessage());
            }
        }
        return Optional.empty();
    }
}
