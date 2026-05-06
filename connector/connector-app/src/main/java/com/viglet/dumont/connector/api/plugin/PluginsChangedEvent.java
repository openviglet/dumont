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

/**
 * Spring application event published whenever a plugin's lifecycle state
 * changes (uploaded, started, stopped, disabled, enabled, deleted). Listeners
 * use this to invalidate caches that may hold stale plugin classloaders or
 * instances.
 */
public record PluginsChangedEvent(String pluginId, String operation) {
}
