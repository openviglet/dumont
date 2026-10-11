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

package com.viglet.dumont.connector.plugin.jobs.source.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DumJobContractParserTest {

    /** text | contract. */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            CLT                                           | clt
            clt                                           | clt
            Efetivo                                       | clt
            CLT (Efetivo)                                 | clt
            Contratação efetiva                           | clt
            Registro em carteira assinada                 | clt
            Regime celetista                              | clt
            'CLT, Tempo integral'                         | clt
            Desenvolvedor CLT - 100% remoto               | clt
            PJ                                            | pj
            Analista PJ                                   | pj
            Pessoa Jurídica                               | pj
            Estágio                                       | estagio
            Estágio em Administração                      | estagio
            Estagiária de Marketing                       | estagio
            ESTAGIÁRIO DE DIREITO                         | estagio
            Temporário                                    | temporario
            Auxiliar de Produção - Temporário             | temporario
            Vaga temporária para o fim de ano             | temporario
            Jovem Aprendiz                                | aprendiz
            Menor Aprendiz                                | aprendiz
            Programa de Trainee 2027                      | trainee
            Freelancer                                    | freelancer
            Freela de design                              | freelancer
            Freelance                                     | freelancer
            Temporário com possibilidade de efetivação    | temporario
            """)
    void namesOneKind(String text, String contract) {
        assertThat(DumJobContractParser.parse(text)).contains(contract);
    }

    @ParameterizedTest(name = "{0}")
    @NullAndEmptySource
    @ValueSource(strings = {
            // Two different kinds: never guessed.
            "CLT ou PJ", "Estágio ou Temporário", "Temporário com chance de efetivação CLT",
            "Trainee ou estagiário", "Contratação CLT ou freelancer",
            // Plurals name the people a role deals with, not its contract.
            "Supervisor de Estagiários", "Instrutor de Aprendizes", "Gestor de Temporários",
            "Coordenador de Trainees",
            // Word boundaries: no kind hides inside another word.
            "Possibilidade de efetivação", "Aprendizado de máquina", "Analista de Temporariedade",
            "Clterra Ltda",
            // Working time is not a contract kind.
            "Full-time", "Tempo integral", "Meio período", "Auxiliar Administrativo" })
    void namesNoKindOrSeveral(String text) {
        assertThat(DumJobContractParser.parse(text)).isEmpty();
    }

    @Test
    void theVocabularyIsTrabalhareis() {
        assertThat(DumJobContractParser.VALUES).containsExactlyInAnyOrder("clt", "pj", "estagio",
                "temporario", "trainee", "freelancer", "aprendiz");
    }
}
