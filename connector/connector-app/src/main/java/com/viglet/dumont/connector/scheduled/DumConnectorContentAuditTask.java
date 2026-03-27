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

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
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
    private final boolean queryBuilderEnabled;
    private final int parallelism;

    public DumConnectorContentAuditTask(DumConnectorPlugin plugin,
            DumConnectorIndexingService indexingService,
            @Value("${dumont.aem.querybuilder:false}") boolean queryBuilderEnabled,
            @Value("${dumont.aem.querybuilder.parallelism:10}") int parallelism) {
        this.plugin = plugin;
        this.indexingService = indexingService;
        this.queryBuilderEnabled = queryBuilderEnabled;
        this.parallelism = Math.max(1, parallelism);
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
        log.info("Auditing source '{}' (queryBuilder={}, parallelism={})",
                source, queryBuilderEnabled, parallelism);
        Date startTime = new Date();
        try {
            List<String> discoveredIds = plugin.discoverContentIds(source);

            AtomicInteger created = new AtomicInteger();
            ConcurrentLinkedQueue<String> allEnvironments = new ConcurrentLinkedQueue<>();
            ConcurrentLinkedQueue<String> allSites = new ConcurrentLinkedQueue<>();
            AtomicReference<Locale> firstLocale = new AtomicReference<>();

            if (queryBuilderEnabled) {
                auditParallel(discoveredIds, source, provider, created,
                        allEnvironments, allSites, firstLocale);
            } else {
                auditSequential(discoveredIds, source, provider, created,
                        allEnvironments, allSites, firstLocale);
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
                    .environment(String.join(", ", new LinkedHashSet<>(allEnvironments)))
                    .locale(firstLocale.get())
                    .sites(new ArrayList<>(new LinkedHashSet<>(allSites)))
                    .build());

            log.info("Audit for source '{}': discovered={}, new NOT_PROCESSED records={}, duration={}ms",
                    source, discoveredIds.size(), created.get(), durationMs);
        } catch (Exception e) {
            log.error("Error auditing source '{}': {}", source, e.getMessage(), e);
        }
    }

    private void auditParallel(List<String> discoveredIds, String source, String provider,
            AtomicInteger created, ConcurrentLinkedQueue<String> allEnvironments,
            ConcurrentLinkedQueue<String> allSites, AtomicReference<Locale> firstLocale) {
        try (ForkJoinPool pool = new ForkJoinPool(parallelism)) {
            pool.submit(() -> discoveredIds.parallelStream()
                    .forEach(objectId -> processAuditObject(objectId, source, provider,
                            created, allEnvironments, allSites, firstLocale))).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Audit interrupted for source '{}'", source, e);
        } catch (Exception e) {
            log.error("Parallel audit error for source '{}'", source, e);
        }
    }

    private void auditSequential(List<String> discoveredIds, String source, String provider,
            AtomicInteger created, ConcurrentLinkedQueue<String> allEnvironments,
            ConcurrentLinkedQueue<String> allSites, AtomicReference<Locale> firstLocale) {
        for (String objectId : discoveredIds) {
            processAuditObject(objectId, source, provider, created,
                    allEnvironments, allSites, firstLocale);
        }
    }

    private void processAuditObject(String objectId, String source, String provider,
            AtomicInteger created, ConcurrentLinkedQueue<String> allEnvironments,
            ConcurrentLinkedQueue<String> allSites, AtomicReference<Locale> firstLocale) {
        try {
            AuditResult result = auditObject(objectId, source, provider);
            created.addAndGet(result.created);
            allEnvironments.addAll(result.environments);
            allSites.addAll(result.sites);
            firstLocale.compareAndSet(null, result.locale);
        } catch (Exception e) {
            log.error("Error auditing objectId '{}': {}", objectId, e.getMessage(), e);
        }
    }

    private record AuditResult(int created, Set<String> environments, Set<String> sites, Locale locale) {}

    private AuditResult auditObject(String objectId, String source, String provider) {
        Locale locale = plugin.resolveLocale(source, objectId);
        List<DumConnectorPlugin.EnvironmentInfo> environments =
                plugin.resolveEnvironments(source, objectId);
        int created = 0;
        Set<String> envNames = new LinkedHashSet<>();
        Set<String> siteNames = new LinkedHashSet<>();

        if (environments.isEmpty()) {
            removeStaleUnprocessedRecords(objectId, source, provider);
        } else {
            Set<String> activeEnvs = environments.stream()
                    .map(DumConnectorPlugin.EnvironmentInfo::environment)
                    .collect(Collectors.toSet());
            removeStaleUnprocessedRecords(objectId, source, provider, activeEnvs);

            for (DumConnectorPlugin.EnvironmentInfo envInfo : environments) {
                envNames.add(envInfo.environment());
                siteNames.addAll(envInfo.sites());
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
        return new AuditResult(created, envNames, siteNames, locale);
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
