/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */

package com.viglet.dumont.connector.plugin;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Registry of all active {@link DumConnectorPlugin} beans, enabling several connector plugins
 * (AEM, EDS, DB, Web Crawler, Assets, ...) to coexist in a single connector instance.
 *
 * <p>
 * Connector-level requests are routed to the right plugin by source ownership
 * ({@link DumConnectorPlugin#ownsSource(String)}) or by provider name. The no-op
 * {@code DEFAULT} plugin is kept out of the active set and used only as a fallback when no
 * plugin manages a given source.
 */
@Slf4j
@Component
public class DumConnectorPluginRegistry {

    /** Provider name reported by the no-op fallback plugin. */
    static final String DEFAULT_PROVIDER = "DEFAULT";

    private final List<DumConnectorPlugin> connectorPlugins;
    private final DumConnectorPlugin defaultPlugin;

    public DumConnectorPluginRegistry(List<DumConnectorPlugin> plugins) {
        this.connectorPlugins = plugins.stream()
                .filter(plugin -> !DEFAULT_PROVIDER.equals(plugin.getProviderName()))
                .toList();
        this.defaultPlugin = plugins.stream()
                .filter(plugin -> DEFAULT_PROVIDER.equals(plugin.getProviderName()))
                .findFirst()
                .orElseGet(() -> connectorPlugins.isEmpty() ? null : connectorPlugins.getFirst());
    }

    @PostConstruct
    void logActivePlugins() {
        if (connectorPlugins.isEmpty()) {
            log.info("No connector plugins active — only the DEFAULT no-op plugin is available.");
        } else {
            log.info("Active connector plugin(s): {}", providerNames());
        }
    }

    /** All active connector plugins (the no-op DEFAULT plugin is excluded). */
    public List<DumConnectorPlugin> connectorPlugins() {
        return connectorPlugins;
    }

    /** Distinct provider names of all active connector plugins. */
    public List<String> providerNames() {
        return connectorPlugins.stream()
                .map(DumConnectorPlugin::getProviderName)
                .distinct()
                .toList();
    }

    /**
     * Resolve the plugin that owns the given source. Falls back to the DEFAULT no-op plugin when
     * no active plugin manages the source.
     */
    public DumConnectorPlugin resolveBySource(String source) {
        return connectorPlugins.stream()
                .filter(plugin -> plugin.ownsSource(source))
                .findFirst()
                .orElse(defaultPlugin);
    }

    /** Provider name of the plugin owning the source (DEFAULT when unmatched). */
    public String resolveProviderBySource(String source) {
        DumConnectorPlugin plugin = resolveBySource(source);
        return plugin != null ? plugin.getProviderName() : DEFAULT_PROVIDER;
    }

    /** Resolve a plugin by its provider name, if active. */
    public Optional<DumConnectorPlugin> resolveByProvider(String provider) {
        return connectorPlugins.stream()
                .filter(plugin -> plugin.getProviderName().equals(provider))
                .findFirst();
    }
}
