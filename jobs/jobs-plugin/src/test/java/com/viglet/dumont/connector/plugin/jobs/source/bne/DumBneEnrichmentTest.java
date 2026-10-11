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

package com.viglet.dumont.connector.plugin.jobs.source.bne;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.plugin.jobs.source.EnrichedRun;
import com.viglet.dumont.connector.plugin.jobs.source.MutableClock;

/** The BNE fixtures through the whole jobs enrichment chain (trabalharei TRA21). */
class DumBneEnrichmentTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    private static EnrichedRun run() {
        MutableClock clock = new MutableClock(NOW);
        return EnrichedRun.of(new DumBneAdapter(new BneFixtureFetcher(), clock), DumBneAdapterTest.source(null),
                clock);
    }

    @Test
    void contractAndSeniorityFromTheTitleThenTheDescription() {
        EnrichedRun run = run();

        assertThat(run.values("bne-4410001")).containsEntry("seniority", List.of("pleno"))
                .doesNotContainKey("contract");
        assertThat(run.values("bne-4410002")).containsEntry("contract", List.of("estagio"))
                .containsEntry("seniority", List.of("estagio"));
        assertThat(run.values("bne-4410003")).containsEntry("contract", List.of("temporario"));
        assertThat(run.values("bne-4410004")).containsEntry("contract", List.of("aprendiz"));
        for (String id : List.of("bne-4410005", "bne-4410006")) {
            assertThat(run.values(id)).doesNotContainKeys("contract", "seniority");
        }
        // BNE states no salary; nothing is invented.
        run.records.values().forEach(rec -> assertThat(rec.values()).doesNotContainKeys("salaryMin",
                "salaryMax", "salaryPeriod", "salaryBand"));
    }

    @Test
    void occupationIsTheCboFamilyOfTheTitleAndAbsentWhenTheTitleNamesNoRole() {
        assertThat(run().occupationsByTitle()).isEqualTo(Map.of(
                "Desenvolvedor Java Pleno", "analistas-de-tecnologia-da-informacao",
                "Estágio em Administração", "-",
                "Auxiliar de Produção - Temporário", "alimentadores-de-linhas-de-producao",
                "Jovem APRENDIZ", "-",
                "Vendedor Externo", "especialistas-em-promocao-de-produtos-e-vendas",
                "Analista de Suporte Híbrido", "analistas-de-tecnologia-da-informacao"));
    }
}
