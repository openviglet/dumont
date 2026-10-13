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

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingStatsModel;
import com.viglet.dumont.connector.persistence.repository.DumConnectorIndexingStatsRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;

/**
 * A source's last run as metrics (roadmap <strong>D136</strong>), so an alert outside the console
 * can see a failed or stalled schedule: {@code dumont_run_last_status} (1 complete, 0 partial),
 * {@code dumont_run_last_finished_seconds} (epoch seconds, for {@code time() - ...} staleness),
 * {@code dumont_run_last_duration_seconds}, {@code dumont_run_last_documents} and
 * {@code dumont_runs_total} by outcome, each tagged with {@code source} and {@code provider}.
 *
 * <p>
 * Fed from the run record itself, at the moment its row is written, so the metric and the console
 * never disagree. The gauges are seeded from the stored records at startup: a restart must not
 * make a stalled source look as if it never ran.
 */
@Slf4j
@Component
public class DumRunMetrics {

    static final String STATUS = "dumont.run.last.status";
    static final String FINISHED = "dumont.run.last.finished";
    static final String DURATION = "dumont.run.last.duration";
    static final String DOCUMENTS = "dumont.run.last.documents";
    static final String RUNS = "dumont.runs";

    /** How many recent records the startup seed reads to find each source's last run. */
    private static final int SEED_LIMIT = 1000;

    private record LastRun(double status, double finishedSeconds, double durationSeconds,
            double documents) {
    }

    private final MeterRegistry registry;
    private final DumConnectorIndexingStatsRepository statsRepository;
    private final Map<String, AtomicReference<LastRun>> lastRuns = new ConcurrentHashMap<>();

    public DumRunMetrics(MeterRegistry registry, DumConnectorIndexingStatsRepository statsRepository) {
        this.registry = registry;
        this.statsRepository = statsRepository;
    }

    /** Records a run that just ended: its gauges move and its outcome is counted. */
    public void recordRun(DumConnectorIndexingStatsModel run) {
        if (!setLast(run)) {
            return;
        }
        Counter.builder(RUNS).description("Connector runs that ended, by outcome")
                .tags(tags(run).and("outcome", outcome(run))).register(registry).increment();
    }

    /** Seeds each source's gauges from its last stored run, so a restart keeps the staleness. */
    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        try {
            List<DumConnectorIndexingStatsModel> recent = statsRepository
                    .findAllByOrderByStartTimeDesc(Limit.of(SEED_LIMIT));
            Set<String> seen = new HashSet<>();
            recent.stream().filter(run -> run.getEndTime() != null)
                    .filter(run -> seen.add(key(run)) && !lastRuns.containsKey(key(run)))
                    .forEach(this::setLast);
        } catch (RuntimeException e) {
            log.warn("Could not seed the run metrics from the stored runs: {}", e.getMessage());
        }
    }

    private boolean setLast(DumConnectorIndexingStatsModel run) {
        if (run == null || run.getSource() == null || run.getEndTime() == null) {
            return false;
        }
        double duration = run.getStartTime() == null ? 0
                : Duration.between(run.getStartTime(), run.getEndTime()).toMillis() / 1000.0;
        LastRun last = new LastRun(run.isPartial() ? 0 : 1, run.getEndTime().toEpochMilli() / 1000.0,
                duration, run.getDocumentCount());
        lastRuns.computeIfAbsent(key(run), k -> register(run)).set(last);
        return true;
    }

    private AtomicReference<LastRun> register(DumConnectorIndexingStatsModel run) {
        AtomicReference<LastRun> ref = new AtomicReference<>();
        Tags tags = tags(run);
        Gauge.builder(STATUS, ref, r -> r.get() == null ? Double.NaN : r.get().status())
                .description("Whether the source's last run completed (1) or stopped short (0)")
                .tags(tags).strongReference(true).register(registry);
        Gauge.builder(FINISHED, ref, r -> r.get() == null ? Double.NaN : r.get().finishedSeconds())
                .description("When the source's last run ended, in epoch seconds").baseUnit("seconds")
                .tags(tags).strongReference(true).register(registry);
        Gauge.builder(DURATION, ref, r -> r.get() == null ? Double.NaN : r.get().durationSeconds())
                .description("How long the source's last run took").baseUnit("seconds").tags(tags)
                .strongReference(true).register(registry);
        Gauge.builder(DOCUMENTS, ref, r -> r.get() == null ? Double.NaN : r.get().documents())
                .description("Documents the source's last run indexed").tags(tags)
                .strongReference(true).register(registry);
        return ref;
    }

    private static String outcome(DumConnectorIndexingStatsModel run) {
        if (run.isPartial()) {
            return "partial";
        }
        return run.isWarned() ? "warned" : "complete";
    }

    private static Tags tags(DumConnectorIndexingStatsModel run) {
        return Tags.of("source", run.getSource(), "provider",
                run.getProvider() == null ? "" : run.getProvider());
    }

    private static String key(DumConnectorIndexingStatsModel run) {
        return run.getSource() + "|" + run.getProvider();
    }
}
