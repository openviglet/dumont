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

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingStatsModel;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingStatsModel.OperationType;
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

    @Async
    public void auditSourceAsync(String source, String provider) {
        auditSource(source, provider);
    }

    private void auditSource(String source, String provider) {
        log.info("Auditing source '{}'", source);
        Date startTime = new Date();
        try {
            List<String> discoveredIds = plugin.discoverContentIds(source);
            int created = 0;

            for (String objectId : discoveredIds) {
                created += auditObject(objectId, source, provider);
            }

            Date endTime = new Date();
            long durationMs = endTime.getTime() - startTime.getTime();
            double docsPerMinute = durationMs > 0
                    ? (discoveredIds.size() * 60_000.0) / durationMs
                    : 0;
            indexingService.saveStats(DumConnectorIndexingStatsModel.builder()
                    .provider(provider)
                    .source(source)
                    .operationType(OperationType.DRY_SCAN)
                    .startTime(startTime)
                    .endTime(endTime)
                    .documentCount(discoveredIds.size())
                    .documentsPerMinute(docsPerMinute)
                    .build());

            log.info("Audit for source '{}': discovered={}, new NOT_PROCESSED records={}, duration={}ms",
                    source, discoveredIds.size(), created, durationMs);
        } catch (Exception e) {
            log.error("Error auditing source '{}': {}", source, e.getMessage(), e);
        }
    }

    private int auditObject(String objectId, String source, String provider) {
        java.util.Locale locale = plugin.resolveLocale(source, objectId);
        List<DumConnectorPlugin.EnvironmentInfo> environments =
                plugin.resolveEnvironments(source, objectId);
        int created = 0;

        if (environments.isEmpty()) {
            removeStaleUnprocessedRecords(objectId, source, provider);
        } else {
            Set<String> activeEnvs = environments.stream()
                    .map(DumConnectorPlugin.EnvironmentInfo::environment)
                    .collect(Collectors.toSet());
            removeStaleUnprocessedRecords(objectId, source, provider, activeEnvs);

            for (DumConnectorPlugin.EnvironmentInfo envInfo : environments) {
                if (!indexingService.existsByObjectIdAndSourceAndEnvironmentAndProvider(
                        objectId, source, envInfo.environment(), provider)) {
                    indexingService.createUnprocessedRecord(objectId, source, provider, locale,
                            envInfo.environment(), envInfo.sites());
                    created++;
                    log.debug("Created NOT_PROCESSED record for objectId='{}' source='{}' locale='{}' env='{}' sites='{}'",
                            objectId, source, locale, envInfo.environment(), envInfo.sites());
                }
            }
        }
        return created;
    }

    private void removeStaleUnprocessedRecords(String objectId, String source, String provider) {
        List<DumConnectorIndexingModel> staleRecords = indexingService
                .findUnprocessedByObjectIdAndSourceAndProvider(objectId, source, provider);
        if (!staleRecords.isEmpty()) {
            indexingService.deleteAll(staleRecords);
            log.debug("Removed {} stale NOT_PROCESSED records for objectId='{}' source='{}'",
                    staleRecords.size(), objectId, source);
        }
    }

    private void removeStaleUnprocessedRecords(String objectId, String source, String provider,
            Set<String> activeEnvironments) {
        List<DumConnectorIndexingModel> unprocessed = indexingService
                .findUnprocessedByObjectIdAndSourceAndProvider(objectId, source, provider);
        List<DumConnectorIndexingModel> staleRecords = unprocessed.stream()
                .filter(indexing -> indexing.getEnvironment() != null
                        && !activeEnvironments.contains(indexing.getEnvironment()))
                .toList();
        if (!staleRecords.isEmpty()) {
            indexingService.deleteAll(staleRecords);
            log.debug("Removed {} stale NOT_PROCESSED records for objectId='{}' source='{}' (not in envs: {})",
                    staleRecords.size(), objectId, source, activeEnvironments);
        }
    }
}
