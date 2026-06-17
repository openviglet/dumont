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

package com.viglet.dumont.connector.plugin.catalog;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.viglet.dumont.connector.plugin.catalog.persistence.repository.DumCatalogSourceRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Async entry point for catalog indexing triggered from the REST API, so the HTTP call returns
 * immediately while the bridge fetches and republishes the source. The structured indexing loop
 * itself lives in {@link DumCatalogPlugin}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class DumCatalogIndexer {

    private final DumCatalogSourceRepository sourceRepository;
    private final DumCatalogPlugin plugin;

    public DumCatalogIndexer(DumCatalogSourceRepository sourceRepository, DumCatalogPlugin plugin) {
        this.sourceRepository = sourceRepository;
        this.plugin = plugin;
    }

    /** Index every record of the source identified by its entity id, asynchronously. */
    @Async
    public void indexAllByIdAsync(String id) {
        sourceRepository.findById(id).ifPresentOrElse(
                source -> plugin.indexAll(source.getName()),
                () -> log.error("Catalog source id '{}' not found", id));
    }
}
