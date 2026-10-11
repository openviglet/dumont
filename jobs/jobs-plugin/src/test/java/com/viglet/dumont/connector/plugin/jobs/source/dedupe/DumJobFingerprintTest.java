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

package com.viglet.dumont.connector.plugin.jobs.source.dedupe;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The fingerprint's normalization, part by part (trabalharei TRA23). */
class DumJobFingerprintTest {

    @ParameterizedTest(name = "title \"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', emptyValue = "", value = {
            "Desenvolvedor Java Pleno               | desenvolvedor java",
            "DESENVOLVEDOR JAVA                     | desenvolvedor java",
            "Desenvolvedor  Java - Sênior           | desenvolvedor java",
            "Desenvolvedor Java Sr.                 | desenvolvedor java",
            "Desenvolvedor Java Jr/Pl               | desenvolvedor java",
            "Vaga: Desenvolvedor Java (Remoto)      | desenvolvedor java",
            "Desenvolvedor Java - Home Office       | desenvolvedor java",
            "Desenvolvedor(a) Java                  | desenvolvedor java",
            "Vendedor (m/f)                         | vendedor",
            "Analista de Sistemas II                | analista sistemas",
            "Técnico em Manutenção Elétrica         | tecnico manutencao eletrica",
            "Auxiliar de Produção - URGENTE         | auxiliar producao",
            "Desenvolvedor C++                      | desenvolvedor c++",
            "Desenvolvedor C#                       | desenvolvedor c#",
            "Desenvolvedor PL/SQL                   | desenvolvedor plsql",
            "Especialista em Dados                  | especialista dados",
            "Gerente de Loja                        | gerente loja",
            "Estagiário de TI                       | estagiario ti",
            "Vaga Urgente                           | ''",
    })
    void normalizesTheTitle(String title, String expected) {
        assertThat(DumJobFingerprint.title(title)).isEqualTo(expected.equals("''") ? "" : expected);
    }

    @ParameterizedTest(name = "company \"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "Acme Tecnologia Ltda.        | acme tecnologia",
            "ACME TECNOLOGIA LTDA         | acme tecnologia",
            "Acme Tecnologia S/A          | acme tecnologia",
            "Acme Tecnologia S.A.         | acme tecnologia",
            "Açaí & Cia ME                | acai cia",
            "Globex Inc.                  | globex",
            "Empresa Confidencial         | ''",
            "Confidencial                 | ''",
            "Sigilosa                     | ''",
            "SA                           | sa",
    })
    void normalizesTheCompany(String company, String expected) {
        assertThat(DumJobFingerprint.company(company)).isEqualTo(expected.equals("''") ? "" : expected);
    }

    @Test
    void aMissingCompanyIsEmpty() {
        assertThat(DumJobFingerprint.company(null)).isEmpty();
        assertThat(DumJobFingerprint.company("  ")).isEmpty();
    }

    @Test
    void normalizesTheCanonicalCity() {
        assertThat(DumJobFingerprint.city("São Paulo", "SP", false)).isEqualTo("sao paulo/SP");
        assertThat(DumJobFingerprint.city("Goianá", "mg", false)).isEqualTo("goiana/MG");
        assertThat(DumJobFingerprint.city("Goianá", null, false)).isEqualTo("goiana");
        assertThat(DumJobFingerprint.city(null, "SP", true)).isEqualTo("remoto");
        assertThat(DumJobFingerprint.city(null, "SP", false)).isEmpty();
    }

    @Test
    void readsTheIsoWeekInBrazilianTime() {
        assertThat(DumJobFingerprint.isoWeek("2026-10-06T10:00:00Z")).isEqualTo("2026-W41");
        assertThat(DumJobFingerprint.isoWeek(LocalDate.parse("2026-10-11"))).isEqualTo("2026-W41");
        // Monday 01:00 UTC is still Sunday evening in São Paulo: the week before.
        assertThat(DumJobFingerprint.isoWeek(Instant.parse("2026-10-12T01:00:00Z"))).isEqualTo("2026-W41");
        assertThat(DumJobFingerprint.isoWeek(Instant.parse("2026-10-12T04:00:00Z"))).isEqualTo("2026-W42");
        // ISO week-based year: 2027-01-01 is a Friday of 2026's last week.
        assertThat(DumJobFingerprint.isoWeek("2027-01-01")).isEqualTo("2026-W53");
        assertThat(DumJobFingerprint.isoWeek("not a date")).isNull();
        assertThat(DumJobFingerprint.isoWeek(null)).isNull();
    }

    @Test
    void theSameOpeningSpelledDifferentlyHasOneKeyAndOneId() {
        DumJobFingerprint.Key adzuna = DumJobFingerprint.of("Desenvolvedor Java Pleno", "Acme Tecnologia Ltda",
                "São Paulo", "SP", false, "2026-10-06T10:00:00Z").orElseThrow();
        DumJobFingerprint.Key jooble = DumJobFingerprint.of("DESENVOLVEDOR JAVA", "ACME TECNOLOGIA S/A",
                "São Paulo", "SP", false, "2026-10-09").orElseThrow();
        assertThat(adzuna).isEqualTo(jooble);
        assertThat(adzuna.text()).isEqualTo("desenvolvedor java|acme tecnologia|sao paulo/SP|2026-W41");
        assertThat(adzuna.groupId()).isEqualTo(jooble.groupId()).startsWith("job-").hasSize(36);
        assertThat(DumJobFingerprint.hashOf(adzuna.groupId())).isEqualTo(adzuna.hash());
    }

    @Test
    void anEmptyCompanyOrCityStaysPartOfTheKey() {
        DumJobFingerprint.Key named = DumJobFingerprint.of("Vendedor", "Lojas Beta", "Campinas", "SP", false,
                "2026-10-06").orElseThrow();
        DumJobFingerprint.Key anonymous = DumJobFingerprint.of("Vendedor", null, "Campinas", "SP", false,
                "2026-10-06").orElseThrow();
        assertThat(anonymous.company()).isEmpty();
        assertThat(anonymous.groupId()).isNotEqualTo(named.groupId());
    }

    @Test
    void noFingerprintWithoutADistinguishingTitleOrADate() {
        assertThat(DumJobFingerprint.of("Vaga urgente", "Acme", "Campinas", "SP", false, "2026-10-06"))
                .isEmpty();
        assertThat(DumJobFingerprint.of("Vendedor", "Acme", "Campinas", "SP", false, null)).isEmpty();
    }

    @Test
    void adapterIdsAreNeverGroupIds() {
        for (String id : new String[] { "adzuna-123", "jooble-9f8e", "bne-77", "jsearch-ab12" }) {
            assertThat(DumJobFingerprint.isGroupId(id)).isFalse();
        }
        assertThat(DumJobFingerprint.isGroupId("job-0123")).isTrue();
    }
}
