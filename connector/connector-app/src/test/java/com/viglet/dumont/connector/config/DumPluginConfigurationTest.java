/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.PluginManager;

import com.viglet.dumont.connector.api.plugin.DumFailedPluginRegistry;

/** Covers {@link DumPluginConfiguration}: PF4J manager creation, startup scan and shutdown. */
class DumPluginConfigurationTest {

    private final DumFailedPluginRegistry failedRegistry = mock(DumFailedPluginRegistry.class);

    private DumPluginConfiguration config(Path dir) {
        return new DumPluginConfiguration(dir.toString(), failedRegistry);
    }

    @Test
    void pluginManagerCreatesMissingDirectory(@TempDir Path tempDir) throws Exception {
        Path missing = tempDir.resolve("plugins-new");
        PluginManager pm = config(missing).pluginManager();

        assertThat(pm).isNotNull();
        assertThat(Files.exists(missing)).isTrue();
    }

    @Test
    void loadPluginsOnStartupReturnsEarlyWhenManagerNull(@TempDir Path tempDir) {
        // pluginManager() was never called, so the manager is null and the scan is skipped.
        config(tempDir).loadPluginsOnStartup();

        verify(failedRegistry, never()).clear();
    }

    @Test
    void loadPluginsOnStartupScansEmptyDirectory(@TempDir Path tempDir) throws Exception {
        when(failedRegistry.list()).thenReturn(List.of());
        DumPluginConfiguration config = config(tempDir);
        config.pluginManager();

        config.loadPluginsOnStartup();

        verify(failedRegistry).clear();
    }

    @Test
    void invalidJarIsRecordedAsFailed(@TempDir Path tempDir) throws Exception {
        Files.write(tempDir.resolve("broken.jar"), new byte[] {0x00, 0x01, 0x02, 0x03});
        lenient().when(failedRegistry.list()).thenReturn(List.of());
        DumPluginConfiguration config = config(tempDir);
        config.pluginManager();

        config.loadPluginsOnStartup();

        verify(failedRegistry).register(any(Path.class), anyString());
    }

    @Test
    void shutdownIsSafeWithAndWithoutManager(@TempDir Path tempDir) throws Exception {
        // No manager initialised → no-op.
        assertThatCode(() -> config(tempDir).onShutdown()).doesNotThrowAnyException();

        // Manager initialised → stop/unload must not throw.
        DumPluginConfiguration config = config(tempDir);
        config.pluginManager();
        assertThatCode(config::onShutdown).doesNotThrowAnyException();
    }
}
