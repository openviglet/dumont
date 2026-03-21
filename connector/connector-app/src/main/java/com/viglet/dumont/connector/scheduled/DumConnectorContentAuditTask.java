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

package com.viglet.dumont.connector.scheduled;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DumConnectorContentAuditTask {
    private final DumConnectorPlugin plugin;
    private final DumConnectorIndexingService indexingService;

    public DumConnectorContentAuditTask(DumConnectorPlugin plugin,
            DumConnectorIndexingService indexingService) {
        this.plugin = plugin;
        this.indexingService = indexingService;
    }

    @Scheduled(cron = "${dumont.audit.cron:-}", zone = "${dumont.audit.cron.zone:UTC}")
    public void auditContent() {
        String provider = plugin.getProviderName();
        List<String> sources = indexingService.getAllSources(provider);

        if (sources.isEmpty()) {
            log.info("No sources found for provider '{}', skipping audit.", provider);
            return;
        }

        log.info("Starting content audit for {} source(s)", sources.size());

        for (String source : sources) {
            auditSource(source, provider);
        }

        log.info("Content audit completed.");
    }

    private void auditSource(String source, String provider) {
        log.info("Auditing source '{}'", source);
        try {
            List<String> discoveredIds = plugin.discoverContentIds(source);
            int created = 0;

            for (String objectId : discoveredIds) {
                if (!indexingService.existsByObjectIdAndSourceAndProvider(objectId, source, provider)) {
                    indexingService.createUnprocessedRecord(objectId, source, provider);
                    created++;
                    log.debug("Created NOT_PROCESSED record for objectId='{}' source='{}'", objectId, source);
                }
            }

            log.info("Audit for source '{}': discovered={}, new NOT_PROCESSED records={}",
                    source, discoveredIds.size(), created);
        } catch (Exception e) {
            log.error("Error auditing source '{}': {}", source, e.getMessage(), e);
        }
    }
}
