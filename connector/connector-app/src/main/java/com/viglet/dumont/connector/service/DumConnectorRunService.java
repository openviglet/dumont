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

package com.viglet.dumont.connector.service;

import org.springframework.stereotype.Service;

import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingStatsModel.OperationType;

/**
 * The one way a whole-source run starts, for the API and the scheduler alike (D124, D128): take
 * the source's run lock, track the start, and hand the run to its plugin. The lock is released by
 * {@code finishIndexing} when the run ends; a start that throws before the plugin hands the run off
 * releases it here, so a failed start never strands the source.
 */
@Service
public class DumConnectorRunService {

    /** How a start request ended. */
    public enum RunStart {
        STARTED, ALREADY_RUNNING
    }

    private final DumConnectorIndexingService indexingService;

    public DumConnectorRunService(DumConnectorIndexingService indexingService) {
        this.indexingService = indexingService;
    }

    public RunStart start(DumConnectorPlugin plugin, String source, OperationType operation) {
        if (!indexingService.tryStartProcessing(source)) {
            return RunStart.ALREADY_RUNNING;
        }
        try {
            if (operation == OperationType.REINDEX_ALL) {
                indexingService.deleteByProviderAndSource(plugin.getProviderName(), source);
            }
            indexingService.trackIndexingStart(source, plugin.getProviderName(), operation);
            plugin.indexAll(source);
        } catch (RuntimeException e) {
            indexingService.finishProcessing(source);
            throw e;
        }
        return RunStart.STARTED;
    }
}
