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

package com.viglet.dumont.connector.plugin.jobs.source.jooble;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.plugin.jobs.source.EnrichedRun;
import com.viglet.dumont.connector.plugin.jobs.source.MutableClock;
import com.viglet.dumont.connector.plugin.jobs.source.salary.DumJobSalaryEnricher;

/** The Jooble fixtures through the whole jobs enrichment chain (trabalharei TRA21). */
class DumJoobleEnrichmentTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final String NO_ID = DumJoobleMapper.id(null,
            "https://jooble.org/desc/away/no-id-918273?ckey=estagio&pos=4&p=1");

    private static EnrichedRun run() {
        return EnrichedRun.of(new DumJoobleAdapter(new JoobleFixtureFetcher()),
                DumJoobleAdapterTest.source(null), new MutableClock(NOW));
    }

    @Test
    void theSalaryTextIsParsedByTheEnricher() {
        EnrichedRun run = run();

        assertThat(run.values("jooble-7316540912345678901")).containsEntry("salaryMin", 5000.0)
                .containsEntry("salaryMax", 7000.0).containsEntry("salaryPeriod", "mes")
                .containsEntry("salaryBand", List.of("3k-5k"));
        assertThat(run.values("jooble--2208761450129834411")).containsEntry("salaryMin", 3000.0)
                .containsEntry("salaryBand", List.of("1.5k-3k")).doesNotContainKey("salaryMax");
        assertThat(run.values("jooble-5521938827364019922")).containsEntry("salaryMin", 2500.0)
                .containsEntry("salaryMax", 3000.0);
        // Hourly and yearly text, which the old in-mapper parser left out, now read: 18 x 220 and
        // 60,000 / 12, with the original period kept.
        assertThat(run.values("jooble-4410982231774520113")).containsEntry("salaryMin", 3960.0)
                .containsEntry("salaryPeriod", "hora").containsEntry("salaryBand", List.of("3k-5k"));
        assertThat(run.values("jooble-8837261540918273645")).containsEntry("salaryMin", 5000.0)
                .containsEntry("salaryPeriod", "ano").containsEntry("salaryBand", List.of("3k-5k"));
        // "A combinar" and an empty salary state nothing, and are not counted as unparsed.
        for (String id : List.of("jooble-1093847561029384756", NO_ID)) {
            assertThat(run.values(id)).doesNotContainKeys("salaryMin", "salaryMax", "salaryPeriod",
                    "salaryBand");
            assertThat(run.source.posting(id)).doesNotContainKey("salaryUnparsed");
        }
        // The raw text is working data: never published.
        run.records.values().forEach(rec -> assertThat(rec.values())
                .doesNotContainKeys("salaryRaw", "salaryUnparsed"));
        assertThat(run.salary.summary("jooble-br")).isEqualTo(new DumJobSalaryEnricher.Summary(7, 0, 5, 0));
    }

    @Test
    void contractFromTheTypeFirstAndSeniorityFromTheTitle() {
        EnrichedRun run = run();

        assertThat(run.values("jooble-7316540912345678901")).containsEntry("contract", List.of("clt"))
                .containsEntry("seniority", List.of("pleno"));
        assertThat(run.values("jooble--2208761450129834411")).containsEntry("contract", List.of("clt"));
        assertThat(run.values("jooble-4410982231774520113")).containsEntry("contract", List.of("pj"));
        assertThat(run.values("jooble-5521938827364019922")).containsEntry("contract", List.of("temporario"));
        assertThat(run.values("jooble-8837261540918273645")).containsEntry("contract", List.of("clt"))
                .doesNotContainKey("seniority");
        assertThat(run.values(NO_ID)).containsEntry("contract", List.of("estagio"))
                .containsEntry("seniority", List.of("estagio"));
        // "Full-time" names no kind, and neither do the title and snippet.
        assertThat(run.values("jooble-1093847561029384756")).doesNotContainKeys("contract", "seniority");
    }

    @Test
    void occupationIsTheCboFamilyOfTheTitleAndAbsentWhenTheTitleNamesNoRole() {
        assertThat(run().occupationsByTitle()).isEqualTo(Map.of(
                "Desenvolvedor Java Pleno", "analistas-de-tecnologia-da-informacao",
                "Vendedor Externo", "especialistas-em-promocao-de-produtos-e-vendas",
                "Atendente de Suporte", "tecnicos-em-operacao-e-monitoracao-de-computadores",
                "Estagiário Administrativo", "-",
                "Motorista Entregador", "motoristas-de-veiculos-de-cargas-em-geral",
                "Auxiliar de Limpeza", "trabalhadores-nos-servicos-de-manutencao-de-edificacoes",
                "Analista de Dados", "analistas-de-tecnologia-da-informacao"));
    }
}
