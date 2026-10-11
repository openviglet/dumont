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

package com.viglet.dumont.connector.plugin.jobs.source.seniority;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DumJobSeniorityParserTest {

    /** title | seniority. */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            Desenvolvedor Java Júnior                 | junior
            Desenvolvedor Front-end Junior            | junior
            Analista Jr                               | junior
            Analista Contábil Jr.                     | junior
            Dev Front-end Jr                          | junior
            Desenvolvedor Java Pleno                  | pleno
            Analista de Marketing Plena               | pleno
            Analista Pl                               | pleno
            Engenheiro de Software Sênior             | senior
            Senior Software Engineer                  | senior
            Analista Sr                               | senior
            Dev Sr.                                   | senior
            Analista Sr de Planejamento               | senior
            Analista Financeiro Sr - São Paulo        | senior
            Desenvolvedor PL/SQL Sênior               | senior
            Estágio em Administração                  | estagio
            Estagiário de Direito                     | estagio
            Vaga de Estágio em TI                     | estagio
            Especialista em Segurança da Informação   | especialista
            Coordenador de Logística                  | coordenacao
            Coordenadora Pedagógica                   | coordenacao
            Gerente de Vendas – Modelo Híbrido        | gerencia
            Gerente de Loja                           | gerencia
            Engenheiro de Dados Sênior (PJ)           | senior
            Analista Sr / Senior                      | senior
            """)
    void readsOneSeniority(String title, String seniority) {
        assertThat(DumJobSeniorityParser.parse(title)).contains(seniority);
    }

    @ParameterizedTest(name = "{0}")
    @NullAndEmptySource
    @ValueSource(strings = {
            // "Sr." as a form of address.
            "Cuidador para Sr. Idoso", "Motorista Particular de Sr", "Atendimento ao Sr(a)",
            "Sr. João contrata pedreiro", "Acompanhante de Idoso (Sr. 80 anos)",
            "Cuidadora de Sra. acamada",
            // An abbreviation inside another name.
            "Desenvolvedor PL/SQL", "Analista PL/SQL e Oracle",
            // Two different seniorities: never guessed.
            "Analista Jr/Pl", "Desenvolvedor Pleno/Sênior", "Coordenador Sênior",
            "Estagiário ou Júnior",
            // Plurals and compounds name other people or other roles.
            "Supervisor de Estagiários", "Vaga para Gerentes de Contas", "Sub-Gerente de Loja",
            "Instrutor de Juniores",
            // No seniority at all.
            "Auxiliar Administrativo", "Analista de Dados", "Motorista Entregador",
            "Programador Java" })
    void readsNoneOrConflict(String title) {
        assertThat(DumJobSeniorityParser.parse(title)).isEmpty();
    }
}
