/*
 *
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

package com.viglet.dumont.connector.plugin.assets;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.plugin.assets.persistence.repository.DumAssetsSourceRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Off-thread entry point for the Assets connector's {@code /{id}/indexAll} API. The full import runs
 * through the structured-source SPI ({@link DumAssetsPlugin#indexAll(String)}); this only resolves
 * the source by its persistence id and dispatches the run asynchronously so the request returns at
 * once (roadmap <strong>D22</strong>).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class DumAssetsPluginProcess {

    private final DumAssetsSourceRepository repository;
    private final DumAssetsPlugin plugin;

    public DumAssetsPluginProcess(DumAssetsSourceRepository repository, DumAssetsPlugin plugin) {
        this.repository = repository;
        this.plugin = plugin;
    }

    @Async
    public void indexAllByIdAsync(String sourceId) {
        repository.findById(sourceId).ifPresentOrElse(source -> {
            log.info("Starting assets import for source: {}", source.getName());
            plugin.indexAll(source.getName());
        }, () -> log.error("Assets source id '{}' not found", sourceId));
    }
}
