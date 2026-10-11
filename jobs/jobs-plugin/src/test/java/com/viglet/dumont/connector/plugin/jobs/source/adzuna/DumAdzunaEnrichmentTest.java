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

package com.viglet.dumont.connector.plugin.jobs.source.adzuna;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.source.EnrichedRun;
import com.viglet.dumont.connector.plugin.jobs.source.MutableClock;
import com.viglet.dumont.connector.plugin.jobs.source.salary.DumJobSalaryEnricher;

/** The Adzuna fixtures through the whole jobs enrichment chain (trabalharei TRA21). */
class DumAdzunaEnrichmentTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    private static EnrichedRun run() {
        MutableClock clock = new MutableClock(NOW);
        DumJobSource source = DumJobSource.builder().id("src-adzuna").name("adzuna-br").sourceType("adzuna")
                .appId("my-app-id").appKey("my-app-key").settings("{\"categories\":[]}")
                .turSNSites(Set.of("trabalharei")).build();
        return EnrichedRun.of(new DumAdzunaAdapter(new FixtureFetcher(), clock), source, clock);
    }

    @Test
    void partnerSalariesPassThroughAndTheEnricherFillsTheBand() {
        EnrichedRun run = run();

        assertThat(run.values("adzuna-4876543210")).containsEntry("salaryMin", 5000.0)
                .containsEntry("salaryMax", 7000.0).containsEntry("salaryPeriod", "ano")
                .containsEntry("salaryBand", List.of("3k-5k"));
        // 1,500/mo is the top of the first band; a minimum alone sets no maximum.
        assertThat(run.values("adzuna-4876542987")).containsEntry("salaryMin", 1500.0)
                .containsEntry("salaryBand", List.of("ate-1.5k")).doesNotContainKey("salaryMax");
        assertThat(run.values("adzuna-4876542777")).containsEntry("salaryBand", List.of("1.5k-3k"));
        assertThat(run.values("adzuna-4876542650")).containsEntry("salaryBand", List.of("1.5k-3k"));
        assertThat(run.values("adzuna-4876542801")).containsEntry("salaryMin", 15000.0)
                .containsEntry("salaryBand", List.of("12k+"));
        // A predicted salary was never stated, and the enricher invents none.
        assertThat(run.values("adzuna-4876543155")).doesNotContainKeys("salaryMin", "salaryMax",
                "salaryPeriod", "salaryBand");
        assertThat(run.records.get("adzuna-4876543210").provenance().get("salaryBand").toString())
                .contains(DumJobSalaryEnricher.NAME);
        assertThat(run.salary.summary("adzuna-br")).isEqualTo(new DumJobSalaryEnricher.Summary(7, 5, 0, 0));
    }

    @Test
    void contractFromContractTypeAndSeniorityFromTheTitle() {
        EnrichedRun run = run();

        assertThat(run.values("adzuna-4876543210")).containsEntry("contract", List.of("clt"))
                .containsEntry("seniority", List.of("pleno"));
        assertThat(run.values("adzuna-4876543155")).containsEntry("contract", List.of("pj"))
                .doesNotContainKey("seniority");
        assertThat(run.values("adzuna-4876542801")).containsEntry("contract", List.of("clt"))
                .containsEntry("seniority", List.of("gerencia"));
        // No contract_type, and neither the title nor the description names a kind.
        assertThat(run.values("adzuna-4876543099")).doesNotContainKeys("contract", "seniority");
        assertThat(run.values("adzuna-4876542777")).doesNotContainKey("contract");
    }

    @Test
    void occupationIsTheCboFamilyOfTheTitleAndAbsentWhenTheTitleNamesNoRole() {
        assertThat(run().occupationsByTitle()).isEqualTo(Map.of(
                "Desenvolvedor Java Pleno", "analistas-de-tecnologia-da-informacao",
                "Auxiliar Administrativo", "agentes-assistentes-e-auxiliares-administrativos",
                "Analista de Dados", "analistas-de-tecnologia-da-informacao",
                "Técnico(a) de Enfermagem – Plantão Noturno", "tecnicos-e-auxiliares-de-enfermagem",
                "Gerente de Vendas – Modelo Híbrido", "gerentes-de-comercializacao-marketing-e-comunicacao",
                "Atendente de Telemarketing Home Office", "operadores-de-telemarketing",
                "Motorista Entregador", "motoristas-de-veiculos-de-cargas-em-geral"));
    }
}
