/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.db;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.plugin.db.persistence.repository.DumDbSourceRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Off-thread entry point for the DB connector's {@code /{id}/indexAll} API. The full import runs
 * through the structured-source SPI ({@link DumDbPlugin#indexAll(String)}); this only resolves the
 * source by its persistence id and dispatches the run asynchronously so the request returns at once.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class DumDbPluginProcess {

    private final DumDbSourceRepository dumDbSourceRepository;
    private final DumDbPlugin dumDbPlugin;

    public DumDbPluginProcess(DumDbSourceRepository dumDbSourceRepository, DumDbPlugin dumDbPlugin) {
        this.dumDbSourceRepository = dumDbSourceRepository;
        this.dumDbPlugin = dumDbPlugin;
    }

    @Async
    public void indexAllByIdAsync(String sourceId) {
        dumDbSourceRepository.findById(sourceId).ifPresentOrElse(
                source -> {
                    log.info("Starting DB import for source: {}", source.getName());
                    dumDbPlugin.indexAll(source.getName());
                },
                () -> log.error("DB source id '{}' not found", sourceId));
    }
}
