/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.viglet.dumont.connector.api.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.viglet.dumont.connector.api.plugin.DumFailedPluginRegistry.FailedPlugin;

class DumFailedPluginRegistryTest {

    @Test
    void registerReadsPluginIdFromPluginProperties(@TempDir Path dir) throws Exception {
        Path jar = dir.resolve("good.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("plugin.properties"));
            out.write(("plugin.id=my-plugin\nplugin.version=1.2.3\nplugin.provider=viglet\n")
                    .getBytes());
            out.closeEntry();
        }
        DumFailedPluginRegistry registry = new DumFailedPluginRegistry();

        registry.register(jar, "boom");

        FailedPlugin entry = registry.get("good.jar");
        assertThat(entry).isNotNull();
        assertThat(entry.pluginId()).isEqualTo("my-plugin");
        assertThat(entry.version()).isEqualTo("1.2.3");
        assertThat(entry.error()).isEqualTo("boom");
    }

    @Test
    void registerReadsPluginIdFromManifestWhenNoProperties(@TempDir Path dir) throws Exception {
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue("Plugin-Id", "manifest-plugin");
        attrs.putValue("Plugin-Version", "9.9");
        Path jar = dir.resolve("manifest.jar");
        try (OutputStream fos = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(fos, manifest)) {
            // manifest-only jar
        }
        DumFailedPluginRegistry registry = new DumFailedPluginRegistry();

        registry.register(jar, "dup id");

        assertThat(registry.get("manifest.jar").pluginId()).isEqualTo("manifest-plugin");
    }

    @Test
    void registerWithUnreadableJarStoresEmptyDescriptor(@TempDir Path dir) {
        DumFailedPluginRegistry registry = new DumFailedPluginRegistry();
        registry.register(dir.resolve("missing.jar"), "not found");

        FailedPlugin entry = registry.get("missing.jar");
        assertThat(entry).isNotNull();
        assertThat(entry.pluginId()).isNull();
        assertThat(entry.error()).isEqualTo("not found");
    }

    @Test
    void listRemoveAndClear(@TempDir Path dir) {
        DumFailedPluginRegistry registry = new DumFailedPluginRegistry();
        registry.register(dir.resolve("a.jar"), "e1");
        registry.register(dir.resolve("b.jar"), "e2");
        assertThat(registry.list()).hasSize(2);

        registry.remove("a.jar");
        assertThat(registry.list()).hasSize(1);
        assertThat(registry.get("a.jar")).isNull();

        registry.clear();
        assertThat(registry.list()).isEmpty();
    }
}
