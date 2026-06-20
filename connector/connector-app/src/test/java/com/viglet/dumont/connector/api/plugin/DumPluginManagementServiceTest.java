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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DumPluginManagementServiceTest {

    @Mock
    private PluginManager pluginManager;
    @Mock
    private DumFailedPluginRegistry failedRegistry;
    @Mock
    private ApplicationEventPublisher events;

    private DumPluginManagementService service;

    @BeforeEach
    void setUp() {
        service = new DumPluginManagementService(pluginManager, failedRegistry, events, "./plugins");
    }

    private PluginWrapper loadedPlugin(String id) {
        PluginWrapper pw = org.mockito.Mockito.mock(PluginWrapper.class);
        PluginDescriptor descriptor = org.mockito.Mockito.mock(PluginDescriptor.class);
        lenient().when(pw.getPluginId()).thenReturn(id);
        lenient().when(pw.getPluginPath()).thenReturn(null);
        lenient().when(pw.getPluginState()).thenReturn(PluginState.STARTED);
        lenient().when(pw.getDescriptor()).thenReturn(descriptor);
        lenient().when(descriptor.getVersion()).thenReturn("1.0.0");
        lenient().when(descriptor.getPluginDescription()).thenReturn("desc");
        lenient().when(descriptor.getProvider()).thenReturn("viglet");
        return pw;
    }

    @Test
    void listCombinesLoadedAndFailedPlugins() {
        when(pluginManager.getPlugins()).thenReturn(List.of());
        when(failedRegistry.list()).thenReturn(List.of());
        assertThat(service.list()).isEmpty();
    }

    @Test
    void startStartsAndReturnsInfo() {
        PluginWrapper pw = loadedPlugin("p1");
        when(pluginManager.getPlugin("p1")).thenReturn(pw);
        when(pluginManager.startPlugin("p1")).thenReturn(PluginState.STARTED);

        DumPluginInfo info = service.start("p1");

        assertThat(info.id()).isEqualTo("p1");
        verify(events).publishEvent(org.mockito.ArgumentMatchers.any(PluginsChangedEvent.class));
    }

    @Test
    void stopStopsAndReturnsInfo() {
        PluginWrapper pw = loadedPlugin("p1");
        when(pluginManager.getPlugin("p1")).thenReturn(pw);
        when(pluginManager.stopPlugin("p1")).thenReturn(PluginState.STOPPED);

        assertThat(service.stop("p1").id()).isEqualTo("p1");
    }

    @Test
    void mutationsOnUnknownPluginReturnNotFound() {
        when(pluginManager.getPlugin("missing")).thenReturn(null);
        assertThatThrownBy(() -> service.start("missing"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void disableFailureMapsToUnprocessable() {
        PluginWrapper pw = loadedPlugin("p1");
        when(pluginManager.getPlugin("p1")).thenReturn(pw);
        when(pluginManager.disablePlugin("p1")).thenReturn(false);

        assertThatThrownBy(() -> service.disable("p1"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void enableSuccessReturnsInfo() {
        PluginWrapper pw = loadedPlugin("p1");
        when(pluginManager.getPlugin("p1")).thenReturn(pw);
        when(pluginManager.enablePlugin("p1")).thenReturn(true);

        assertThat(service.enable("p1").id()).isEqualTo("p1");
    }

    @Test
    void deleteSuccessAndFailure() {
        PluginWrapper pw = loadedPlugin("p1");
        when(pluginManager.getPlugin("p1")).thenReturn(pw);
        when(pluginManager.deletePlugin("p1")).thenReturn(true);
        service.delete("p1");
        verify(pluginManager).deletePlugin("p1");

        PluginWrapper pw2 = loadedPlugin("p2");
        when(pluginManager.getPlugin("p2")).thenReturn(pw2);
        when(pluginManager.deletePlugin("p2")).thenReturn(false);
        assertThatThrownBy(() -> service.delete("p2"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deleteFailedRequiresRegisteredEntry() {
        when(failedRegistry.get("missing.jar")).thenReturn(null);
        assertThatThrownBy(() -> service.deleteFailed("missing.jar"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void uploadRejectsNonJarFile() {
        MockMultipartFile file =
                new MockMultipartFile("file", "evil.txt", "text/plain", new byte[] {1, 2});
        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void uploadRejectsNullFilename() {
        MockMultipartFile file =
                new MockMultipartFile("file", null, "application/java-archive", new byte[] {1});
        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(ResponseStatusException.class);
    }
}
