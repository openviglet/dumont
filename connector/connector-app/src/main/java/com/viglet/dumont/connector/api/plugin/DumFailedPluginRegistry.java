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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Tracks JAR files in the plugins directory that could not be loaded by PF4J
 * (duplicate plugin id, missing dependency, corrupted JAR, etc.). These entries
 * are surfaced to the management UI so the failure is not silent.
 */
@Slf4j
@Component
public class DumFailedPluginRegistry {

    public record FailedPlugin(
            String filename,
            String pluginId,
            String version,
            String provider,
            String error,
            Instant lastModified) {
    }

    private final ConcurrentMap<String, FailedPlugin> failures = new ConcurrentHashMap<>();

    public void register(Path jarPath, String error) {
        String filename = jarPath.getFileName().toString();
        JarDescriptor descriptor = readDescriptor(jarPath);
        FailedPlugin entry = new FailedPlugin(
                filename,
                descriptor.pluginId(),
                descriptor.version(),
                descriptor.provider(),
                error,
                lastModifiedOf(jarPath));
        failures.put(filename, entry);
        log.warn("Plugin {} marked as FAILED: {}", filename, error);
    }

    public void remove(String filename) {
        failures.remove(filename);
    }

    public void clear() {
        failures.clear();
    }

    public Collection<FailedPlugin> list() {
        return List.copyOf(failures.values());
    }

    public FailedPlugin get(String filename) {
        return failures.get(filename);
    }

    private static Instant lastModifiedOf(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return null;
        }
    }

    private record JarDescriptor(String pluginId, String version, String provider) {
        static JarDescriptor empty() {
            return new JarDescriptor(null, null, null);
        }
    }

    private static JarDescriptor readDescriptor(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var propsEntry = jar.getJarEntry("plugin.properties");
            if (propsEntry != null) {
                Properties p = new Properties();
                try (InputStream is = jar.getInputStream(propsEntry)) {
                    p.load(is);
                }
                String id = p.getProperty("plugin.id");
                if (id != null) {
                    return new JarDescriptor(id,
                            p.getProperty("plugin.version"),
                            p.getProperty("plugin.provider"));
                }
            }
            Manifest mf = jar.getManifest();
            if (mf != null) {
                var attrs = mf.getMainAttributes();
                String id = attrs.getValue("Plugin-Id");
                if (id != null) {
                    return new JarDescriptor(id,
                            attrs.getValue("Plugin-Version"),
                            attrs.getValue("Plugin-Provider"));
                }
            }
        } catch (IOException e) {
            log.debug("Could not read descriptor from {}: {}", jarPath, e.getMessage());
        }
        return JarDescriptor.empty();
    }
}
