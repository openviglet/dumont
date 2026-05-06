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

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.viglet.dumont.commons.cache.DumCustomClassCache;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DumPluginCacheInvalidator {

    @EventListener
    public void onPluginsChanged(PluginsChangedEvent event) {
        DumCustomClassCache.clear();
        log.info("Cleared DumCustomClassCache after plugin {} ({})",
                event.operation(), event.pluginId());
    }
}
