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

package com.viglet.dumont.connector.plugin.jobs.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DumJobNormalizerTest {

    @Test
    void everyStateMapsToItsUfWithAndWithoutAccents() {
        Map<String, String> states = new LinkedHashMap<>();
        states.put("Acre", "AC");
        states.put("Alagoas", "AL");
        states.put("Amapá", "AP");
        states.put("Amazonas", "AM");
        states.put("Bahia", "BA");
        states.put("Ceará", "CE");
        states.put("Distrito Federal", "DF");
        states.put("Espírito Santo", "ES");
        states.put("Goiás", "GO");
        states.put("Maranhão", "MA");
        states.put("Mato Grosso", "MT");
        states.put("Mato Grosso do Sul", "MS");
        states.put("Minas Gerais", "MG");
        states.put("Pará", "PA");
        states.put("Paraíba", "PB");
        states.put("Paraná", "PR");
        states.put("Pernambuco", "PE");
        states.put("Piauí", "PI");
        states.put("Rio de Janeiro", "RJ");
        states.put("Rio Grande do Norte", "RN");
        states.put("Rio Grande do Sul", "RS");
        states.put("Rondônia", "RO");
        states.put("Roraima", "RR");
        states.put("Santa Catarina", "SC");
        states.put("São Paulo", "SP");
        states.put("Sergipe", "SE");
        states.put("Tocantins", "TO");
        assertThat(states).hasSize(27);

        states.forEach((name, uf) -> {
            assertThat(DumJobNormalizer.uf(name)).as(name).isEqualTo(uf);
            assertThat(DumJobNormalizer.uf(DumJobNormalizer.fold(name))).as("folded " + name)
                    .isEqualTo(uf);
            assertThat(DumJobNormalizer.uf(name.toUpperCase())).as("upper " + name).isEqualTo(uf);
        });
        assertThat(DumJobNormalizer.uf("Sao Paulo")).isEqualTo("SP");
        assertThat(DumJobNormalizer.uf("  rio  grande do  sul ")).isEqualTo("RS");
        assertThat(DumJobNormalizer.uf("sp")).isEqualTo("SP");
    }

    @Test
    void unknownStatesAreNull() {
        assertThat(DumJobNormalizer.uf("Lisboa")).isNull();
        assertThat(DumJobNormalizer.uf("Brasil")).isNull();
        assertThat(DumJobNormalizer.uf("XX")).isNull();
        assertThat(DumJobNormalizer.uf("")).isNull();
        assertThat(DumJobNormalizer.uf(null)).isNull();
    }

    @Test
    void salaryBandsIncludeTheirUpperBound() {
        assertThat(DumJobNormalizer.salaryBand(1200.0)).isEqualTo("ate-1.5k");
        assertThat(DumJobNormalizer.salaryBand(1500.0)).isEqualTo("ate-1.5k");
        assertThat(DumJobNormalizer.salaryBand(1500.01)).isEqualTo("1.5k-3k");
        assertThat(DumJobNormalizer.salaryBand(3000.0)).isEqualTo("1.5k-3k");
        assertThat(DumJobNormalizer.salaryBand(4999.0)).isEqualTo("3k-5k");
        assertThat(DumJobNormalizer.salaryBand(5000.0)).isEqualTo("3k-5k");
        assertThat(DumJobNormalizer.salaryBand(8000.0)).isEqualTo("5k-8k");
        assertThat(DumJobNormalizer.salaryBand(12000.0)).isEqualTo("8k-12k");
        assertThat(DumJobNormalizer.salaryBand(12000.5)).isEqualTo("12k+");
        assertThat(DumJobNormalizer.salaryBand(null)).isNull();
        assertThat(DumJobNormalizer.salaryBand(0.0)).isNull();
    }

    @Test
    void annualToMonthlyRoundsToCents() {
        assertThat(DumJobNormalizer.annualToMonthly(60000)).isEqualTo(5000.0);
        assertThat(DumJobNormalizer.annualToMonthly(50000)).isEqualTo(4166.67);
    }

    @Test
    void slugAndSiteUrl() {
        assertThat(DumJobNormalizer.slug("Técnico(a) de Enfermagem – Plantão Noturno!"))
                .isEqualTo("tecnico-a-de-enfermagem-plantao-noturno");
        assertThat(DumJobNormalizer.slug("  --C++ / C#  ")).isEqualTo("c-c");
        assertThat(DumJobNormalizer.siteUrl(null, "Estágio em Marketing", "fx-estagio-bh"))
                .isEqualTo("https://trabalharei.com.br/vaga/estagio-em-marketing-fx-estagio-bh/");
        assertThat(DumJobNormalizer.siteUrl("https://x.test/vaga", "Vaga", "a-1"))
                .isEqualTo("https://x.test/vaga/vaga-a-1/");
        assertThat(DumJobNormalizer.siteUrl(null, "!!!", "a-1")).isNull();
        assertThat(DumJobNormalizer.siteUrl(null, "Vaga", null)).isNull();
    }

    @Test
    void remoteIsStatedOrAbsent() {
        assertThat(DumJobNormalizer.remote("Vaga 100% Remota")).isEqualTo("remoto");
        assertThat(DumJobNormalizer.remote("Analista", "trabalho em HOME-OFFICE")).isEqualTo("remoto");
        assertThat(DumJobNormalizer.remote("Analista", null, "Remoto")).isEqualTo("remoto");
        assertThat(DumJobNormalizer.remote("Modelo híbrido com home office")).isNull();
        assertThat(DumJobNormalizer.remote("Analista", "Escritório na Paulista")).isNull();
        // A word that merely contains "remot" is not a signal.
        assertThat(DumJobNormalizer.remote("Suporte prestado remotamente quando preciso")).isNull();
    }

    @Test
    void plainTextDropsTags() {
        assertThat(DumJobNormalizer.plainText("Dev <strong>Java</strong>  Pleno")).isEqualTo("Dev Java Pleno");
        assertThat(DumJobNormalizer.plainText(" <br/> ")).isNull();
        assertThat(DumJobNormalizer.plainText(null)).isNull();
    }
}
