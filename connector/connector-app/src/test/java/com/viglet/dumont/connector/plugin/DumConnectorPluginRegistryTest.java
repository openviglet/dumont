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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;

class DumConnectorPluginRegistryTest {

    private static DumConnectorPlugin plugin(String provider, String ownedSource) {
        DumConnectorPlugin plugin = mock(DumConnectorPlugin.class);
        when(plugin.getProviderName()).thenReturn(provider);
        if (ownedSource != null) {
            when(plugin.ownsSource(ownedSource)).thenReturn(true);
        }
        return plugin;
    }

    @Test
    void findBySourceReturnsTheOwnerAndNeverTheFallback() {
        DumConnectorPlugin fallback = plugin(DumConnectorPluginRegistry.DEFAULT_PROVIDER, null);
        DumConnectorPlugin db = plugin("DB", "orders");
        DumConnectorPluginRegistry registry = new DumConnectorPluginRegistry(List.of(fallback, db));

        assertThat(registry.findBySource("orders")).containsSame(db);
        assertThat(registry.findBySource("ghost")).isEmpty();
        assertThat(registry.resolveBySource("ghost")).isSameAs(fallback);
    }
}
