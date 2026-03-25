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

package com.viglet.dumont.connector.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DumConnectorIndexAllByTabService {
    private static final int CHUNK_SIZE = 50;
    private final DumConnectorIndexingService indexingService;
    private final DumConnectorSolrService solrService;
    private final DumConnectorPlugin plugin;

    public DumConnectorIndexAllByTabService(DumConnectorIndexingService indexingService,
            DumConnectorSolrService solrService, DumConnectorPlugin plugin) {
        this.indexingService = indexingService;
        this.solrService = solrService;
        this.plugin = plugin;
    }

    @Async
    public void indexAllByTabAsync(String source, String tab) {
        String provider = plugin.getProviderName();
        log.info("Starting async index-all-by-tab '{}' for source '{}'", tab, source);
        try {
            List<String> allIds = switch (tab) {
                case "unprocessed" -> indexingService
                        .getUnprocessedBySourceAndProvider(source, provider)
                        .stream().map(DumConnectorIndexingModel::getObjectId).toList();
                case "missing" -> solrService.validateContent(source, provider)
                        .getMissing().values().stream().flatMap(List::stream).toList();
                case "extra" -> solrService.validateContent(source, provider)
                        .getExtra().values().stream().flatMap(List::stream).toList();
                default -> List.of();
            };
            if (!allIds.isEmpty()) {
                for (int i = 0; i < allIds.size(); i += CHUNK_SIZE) {
                    List<String> chunk = allIds.subList(i, Math.min(i + CHUNK_SIZE, allIds.size()));
                    plugin.indexById(source, new ArrayList<>(chunk));
                }
                log.info("Index all by tab '{}' for source '{}': {} items sent in {} chunk(s)",
                        tab, source, allIds.size(), (allIds.size() + CHUNK_SIZE - 1) / CHUNK_SIZE);
            } else {
                log.info("Index all by tab '{}' for source '{}': no items found", tab, source);
            }
        } catch (Exception e) {
            log.error("Error during async index-all-by-tab '{}' for source '{}': {}",
                    tab, source, e.getMessage(), e);
        }
    }
}
