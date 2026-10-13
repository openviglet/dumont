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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingStatsModel;
import com.viglet.dumont.connector.persistence.repository.DumConnectorIndexingStatsRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

/** D136: a source's last run is readable by an alert outside the console. */
class DumRunMetricsTest {

    private static final Instant START = Instant.parse("2026-10-13T10:00:00Z");

    private SimpleMeterRegistry registry;
    private DumConnectorIndexingStatsRepository statsRepository;
    private DumRunMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        statsRepository = mock(DumConnectorIndexingStatsRepository.class);
        metrics = new DumRunMetrics(registry, statsRepository);
    }

    private static DumConnectorIndexingStatsModel run(String source, boolean partial, long docs,
            List<String> warnings) {
        return DumConnectorIndexingStatsModel.builder().source(source).provider("turing")
                .startTime(START).endTime(START.plusSeconds(90)).documentCount(docs).partial(partial)
                .notes(new ArrayList<>()).warnings(new ArrayList<>(warnings)).build();
    }

    private double gauge(String name, String source) {
        return registry.get(name).tag("source", source).tag("provider", "turing").gauge().value();
    }

    private double runs(String source, String outcome) {
        return registry.get(DumRunMetrics.RUNS).tag("source", source).tag("outcome", outcome).counter()
                .count();
    }

    @Test
    void aCompleteRunReportsItsStatusFinishDurationAndDocuments() {
        metrics.recordRun(run("jobs", false, 42, List.of()));

        assertThat(gauge(DumRunMetrics.STATUS, "jobs")).isEqualTo(1);
        assertThat(gauge(DumRunMetrics.FINISHED, "jobs")).isEqualTo(START.plusSeconds(90).getEpochSecond());
        assertThat(gauge(DumRunMetrics.DURATION, "jobs")).isEqualTo(90);
        assertThat(gauge(DumRunMetrics.DOCUMENTS, "jobs")).isEqualTo(42);
        assertThat(runs("jobs", "complete")).isEqualTo(1);
    }

    @Test
    void aPartialRunDropsTheStatusAndTheNextCompleteRunRaisesIt() {
        metrics.recordRun(run("jobs", true, 3, List.of()));
        assertThat(gauge(DumRunMetrics.STATUS, "jobs")).isZero();
        assertThat(runs("jobs", "partial")).isEqualTo(1);

        metrics.recordRun(run("jobs", false, 40, List.of("partner slow")));
        assertThat(gauge(DumRunMetrics.STATUS, "jobs")).isEqualTo(1);
        assertThat(runs("jobs", "warned")).isEqualTo(1);
    }

    @Test
    void sourcesAreReportedApart() {
        metrics.recordRun(run("a", true, 1, List.of()));
        metrics.recordRun(run("b", false, 2, List.of()));

        assertThat(gauge(DumRunMetrics.STATUS, "a")).isZero();
        assertThat(gauge(DumRunMetrics.STATUS, "b")).isEqualTo(1);
    }

    @Test
    void startupSeedsEachSourceFromItsLastStoredRunOnly() {
        // Newest first, as the repository orders them: "a"'s last run was partial.
        when(statsRepository.findAllByOrderByStartTimeDesc(any())).thenReturn(List.of(
                run("a", true, 1, List.of()), run("a", false, 9, List.of()), run("b", false, 5, List.of())));

        metrics.seed();

        assertThat(gauge(DumRunMetrics.STATUS, "a")).isZero();
        assertThat(gauge(DumRunMetrics.DOCUMENTS, "a")).isEqualTo(1);
        assertThat(gauge(DumRunMetrics.STATUS, "b")).isEqualTo(1);
        // A seed restores state; it is not a run that ended.
        assertThat(registry.find(DumRunMetrics.RUNS).counters()).isEmpty();
    }

    @Test
    void aSeedNeverOverwritesARunThatEndedFirst() {
        metrics.recordRun(run("a", false, 7, List.of()));
        when(statsRepository.findAllByOrderByStartTimeDesc(any()))
                .thenReturn(List.of(run("a", true, 1, List.of())));

        metrics.seed();

        assertThat(gauge(DumRunMetrics.STATUS, "a")).isEqualTo(1);
    }

    @Test
    void aSeedThatCannotReadTheStoreLeavesTheMetricsEmpty() {
        when(statsRepository.findAllByOrderByStartTimeDesc(any())).thenThrow(new IllegalStateException("db"));

        metrics.seed();

        assertThat(registry.find(DumRunMetrics.STATUS).gauges()).isEmpty();
    }

    @Test
    void theScrapeCarriesThePrometheusNamesAnAlertRuleUses() {
        PrometheusMeterRegistry prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        new DumRunMetrics(prometheus, statsRepository).recordRun(run("jobs", false, 4, List.of()));

        assertThat(prometheus.scrape()).contains("dumont_run_last_status{provider=\"turing\",source=\"jobs\"} 1.0",
                "dumont_run_last_finished_seconds{", "dumont_run_last_duration_seconds{",
                "dumont_run_last_documents{", "dumont_runs_total{outcome=\"complete\"");
    }
}
