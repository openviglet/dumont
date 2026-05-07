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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.pf4j.PluginWrapper;

public record DumPluginInfo(
        String id,
        String version,
        String description,
        String provider,
        String state,
        String path,
        Instant lastModified) {

    public static DumPluginInfo from(PluginWrapper wrapper) {
        Path pluginPath = wrapper.getPluginPath();
        return new DumPluginInfo(
                wrapper.getPluginId(),
                wrapper.getDescriptor().getVersion(),
                wrapper.getDescriptor().getPluginDescription(),
                wrapper.getDescriptor().getProvider(),
                wrapper.getPluginState().toString(),
                pluginPath != null ? pluginPath.getFileName().toString() : null,
                lastModifiedOf(pluginPath));
    }

    private static Instant lastModifiedOf(Path path) {
        if (path == null) {
            return null;
        }
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (Exception e) {
            return null;
        }
    }
}
