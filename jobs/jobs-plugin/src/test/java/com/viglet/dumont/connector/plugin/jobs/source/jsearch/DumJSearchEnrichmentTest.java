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

package com.viglet.dumont.connector.plugin.jobs.source.jsearch;

import static com.viglet.dumont.connector.plugin.jobs.source.jsearch.DumJSearchAdapterTest.id;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.plugin.jobs.source.EnrichedRun;
import com.viglet.dumont.connector.plugin.jobs.source.MutableClock;

/** The JSearch fixtures through the whole jobs enrichment chain (trabalharei TRA21). */
class DumJSearchEnrichmentTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    private static EnrichedRun run() {
        return EnrichedRun.of(new DumJSearchAdapter(new JSearchFixtureFetcher()),
                DumJSearchAdapterTest.source(null), new MutableClock(NOW));
    }

    @Test
    void partnerSalariesPassThroughAndTheEnricherFillsTheBand() throws Exception {
        EnrichedRun run = run();

        assertThat(run.values(id(DumJSearchAdapterTest.JAVA))).containsEntry("salaryMin", 5000.0)
                .containsEntry("salaryMax", 7000.0).containsEntry("salaryPeriod", "mes")
                .containsEntry("salaryBand", List.of("3k-5k"));
        assertThat(run.values(id(DumJSearchAdapterTest.DADOS))).containsEntry("salaryMin", 10000.0)
                .containsEntry("salaryPeriod", "ano").containsEntry("salaryBand", List.of("8k-12k"));
        assertThat(run.values(id(DumJSearchAdapterTest.SUPORTE))).containsEntry("salaryMin", 3000.0)
                .containsEntry("salaryBand", List.of("1.5k-3k"));
        // An hourly JSearch salary is still left out by the mapper, and the enricher adds none.
        assertThat(run.values(id(DumJSearchAdapterTest.ESTAGIO))).doesNotContainKeys("salaryMin",
                "salaryBand");
    }

    @Test
    void contractFromTheEmploymentTypeThenTitleAndText() throws Exception {
        EnrichedRun run = run();

        assertThat(run.values(id(DumJSearchAdapterTest.DADOS))).containsEntry("contract", List.of("pj"))
                .containsEntry("seniority", List.of("senior"));
        assertThat(run.values(id(DumJSearchAdapterTest.ESTAGIO))).containsEntry("contract", List.of("estagio"))
                .containsEntry("seniority", List.of("estagio"));
        assertThat(run.values(id(DumJSearchAdapterTest.SUPORTE))).containsEntry("contract", List.of("pj"));
        // FULLTIME names no kind; neither do the title and description.
        assertThat(run.values(id(DumJSearchAdapterTest.JAVA))).doesNotContainKey("contract")
                .containsEntry("seniority", List.of("pleno"));
        // "estágio ou PJ" in the text: two kinds, never guessed.
        assertThat(run.values(id(DumJSearchAdapterTest.FRONT))).doesNotContainKey("contract")
                .containsEntry("seniority", List.of("junior"));
    }
}
