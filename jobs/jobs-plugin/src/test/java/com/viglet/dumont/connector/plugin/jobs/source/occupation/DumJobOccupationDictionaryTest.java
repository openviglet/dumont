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

package com.viglet.dumont.connector.plugin.jobs.source.occupation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.viglet.dumont.connector.commons.spi.structured.recipe.DumTaxonomy;

/** The title dictionary over real-looking job-board titles (trabalharei TRA22). */
class DumJobOccupationDictionaryTest {

    private static final DumTaxonomy CBO = DumCboFamilies.load();

    /** An empty code means the title stays unclassified by the dictionary. */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
            # --- the adapters' fixture titles ---
            Desenvolvedor Java <strong>Pleno</strong>        | 2124
            Desenvolvedor Java Pleno                         | 2124
            Engenheiro de Software Sênior                    | 2122
            Auxiliar Administrativo                          | 4110
            Analista de Dados                                | 2124
            Técnico(a) de Enfermagem – Plantão Noturno       | 3222
            Gerente de Vendas – Modelo Híbrido               | 1423
            Atendente de Telemarketing Home Office           | 4223
            Motorista Entregador                             | 7825
            Vendedor Externo                                 | 3541
            Atendente de Suporte                             | 3172
            Estagiário Administrativo                        | -
            Auxiliar de Limpeza                              | 5143
            Estágio em Administração                         | -
            Auxiliar de Produção - Temporário                | 7842
            '  Jovem APRENDIZ  '                             | -
            Analista de Suporte Híbrido                      | 2124
            Engenheiro de Dados Sênior (PJ)                  | 2122
            Software Engineer                                | 2122
            Atendente de Loja - Meio Período                 | 5211
            Analista de Suporte                              | 2124
            Desenvolvedor Front-end Júnior                   | 2124
            Programador Java                                 | 3171
            Vaga sem Id                                      | -
            # --- drivers and logistics ---
            Motorista                                        | 7825
            Motorista Carreteiro CNH E                       | 7825
            Motorista de Caminhão Truck                      | 7825
            Motorista de Ônibus Urbano                       | 7824
            Motorista CNH B                                  | 7823
            Motorista Executivo                              | 7823
            Ajudante de Motorista                            | 7832
            Motoboy Entregador                               | 5191
            Operador de Empilhadeira                         | 7822
            Empilhadeirista - 2º turno                       | 7822
            Estoquista                                       | 4141
            Auxiliar de Logística                            | 4141
            Analista de Logística Pl                         | 2527
            Conferente de Mercadorias                        | 4142
            Auxiliar de Carga e Descarga                     | 7832
            # --- commerce and service ---
            Vendedor(a) Interno                              | 5211
            Vendedora de Loja                                | 5211
            Operador de Caixa                                | 4211
            Operadora de Caixa - Supermercado                | 4211
            Repositor de Mercadorias                         | 5211
            Promotor de Vendas                               | 5211
            Representante Comercial Autônomo                 | 3547
            Corretor de Imóveis                              | 3546
            Recepcionista                                    | 4221
            Recepcionista - Clínica Veterinária              | 4221
            Porteiro Noturno                                 | 5174
            Controlador de Acesso                            | 5174
            Vigilante Patrimonial                            | 5173
            Auxiliar de Serviços Gerais                      | 5143
            Faxineira Diarista                               | 5143
            Zelador                                          | 5141
            Cozinheiro                                       | 5132
            Auxiliar de Cozinha                              | 5135
            Garçom                                           | 5134
            Garçonete - Fins de Semana                       | 5134
            Pizzaiolo                                        | 5136
            Padeiro e Confeiteiro                            | 8483
            Açougueiro                                       | 8485
            Cuidadora de Idosos                              | 5162
            Manicure e Pedicure                              | 5161
            Designer de Sobrancelhas                         | 5161
            # --- health and education ---
            Técnico de Enfermagem                            | 3222
            Auxiliar de Enfermagem                           | 3222
            Enfermeiro(a) Assistencial                       | 2235
            Farmacêutico Responsável Técnico                 | 2234
            Atendente de Farmácia                            | 5211
            Fisioterapeuta                                   | 2236
            Nutricionista Clínica                            | 2237
            Psicóloga                                        | 2515
            Médico Clínico Geral                             | 2251
            Dentista                                         | 2232
            Auxiliar de Saúde Bucal                          | 3224
            Professor de Inglês                              | 3331
            Professora de Educação Infantil                  | 2311
            Professor de Educação Física                     | 2241
            Professor                                        | -
            # --- technology ---
            Desenvolvedor Backend Sr                         | 2124
            Desenvolvedor de Negócios                        | -
            Programador PHP                                  | 3171
            Programador CNC                                  | 7214
            Analista de Sistemas Pleno                       | 2124
            Cientista de Dados                               | 2124
            Analista de Infraestrutura                       | 2123
            Suporte Técnico N1                               | 3172
            Técnico de Informática                           | 3172
            Arquiteto de Software                            | -
            Arquiteto                                        | 2141
            # --- trades and industry ---
            Eletricista                                      | 7156
            Eletricista de Manutenção Industrial             | 9511
            Eletricista Automotivo                           | 9531
            Engenheiro Eletricista                           | 2143
            Mecânico                                         | 9144
            Mecânico de Manutenção                           | 9113
            Mecânico de Refrigeração                         | 9112
            Engenheiro Mecânico                              | 2144
            Pedreiro                                         | 7152
            Servente de Obras                                | 7170
            Pintor Automotivo                                | 7233
            Pintor de Obras                                  | 7166
            Soldador                                         | 7243
            Costureira                                       | 7632
            Técnico em Segurança do Trabalho                 | 3516
            Engenheiro Civil                                 | 2142
            # --- office, finance, people, marketing ---
            Assistente Administrativo                        | 4110
            Jovem Aprendiz Auxiliar Administrativo           | 4110
            Auxiliar de Escritório                           | 4110
            Assistente de Departamento Pessoal               | 4110
            Analista de RH                                   | 2524
            Analista de Recrutamento e Seleção Jr            | 2524
            Contador                                         | 2522
            Analista Contábil Sênior                         | 2522
            Auxiliar Contábil                                | 4131
            Analista Financeiro                              | 2525
            Advogado Trabalhista                             | 2410
            Designer Gráfico                                 | 2624
            Designer de Interiores                           | 2629
            Analista de Marketing                            | 2531
            Social Media                                     | 2531
            Operador de Telemarketing                        | 4223
            Agente de Cobrança                               | 4213
            Secretária                                       | 3515
            Secretária Executiva Bilíngue                    | 2523
            Analista                                         | -
            Atendente                                        | -
            """)
    void readsTheFamilyOfAJobTitle(String title, String code) {
        assertThat(DumJobOccupationDictionary.classify(title).orElse(null)).isEqualTo(code);
        if (code != null) {
            assertThat(CBO.table()).containsKey(code);
        }
    }

    @Test
    void normalizesCaseAccentsGenderMarksAndSeniority() {
        assertThat(DumJobOccupationDictionary.normalize("Técnico(a) de Enfermagem – Plantão"))
                .isEqualTo("tecnico de enfermagem plantao");
        assertThat(DumJobOccupationDictionary.normalize("Desenvolvedor <b>Java</b> Sênior"))
                .isEqualTo("desenvolvedor java");
        assertThat(DumJobOccupationDictionary.normalize("ANALISTA DE DADOS JR."))
                .isEqualTo(DumJobOccupationDictionary.normalize("analista de dados pleno"));
        assertThat(DumJobOccupationDictionary.normalize("  Jovem APRENDIZ  ")).isEmpty();
        assertThat(DumJobOccupationDictionary.normalize("Estagiária de Marketing"))
                .isEqualTo("de marketing");
        assertThat(DumJobOccupationDictionary.normalize(null)).isEmpty();
    }

    @Test
    void seniorityWordsDoNotChangeTheFamily() {
        for (String level : new String[] { "Jr", "Júnior", "Pleno", "Pl", "Sr", "Sênior", "Trainee" }) {
            assertThat(DumJobOccupationDictionary.classify("Analista de Sistemas " + level))
                    .as(level).contains("2124");
        }
    }

    @Test
    void wordBoundariesKeepPartsOfWordsFromMatching() {
        // "vigia" is not read inside "vigilante", "caixa" only as the whole title.
        assertThat(DumJobOccupationDictionary.classify("Vigilante")).contains("5173");
        assertThat(DumJobOccupationDictionary.classify("Montador de Caixas de Papelão")).isEmpty();
        assertThat(DumJobOccupationDictionary.classify("Caixa")).contains("4211");
        // "dev" is not read inside "devolução".
        assertThat(DumJobOccupationDictionary.classify("Auxiliar de Devolução")).isEmpty();
    }

    @Test
    void holdsAtLeastEightyRulesEachNamingAShippedFamily() {
        assertThat(DumJobOccupationDictionary.rules()).hasSizeGreaterThanOrEqualTo(80);
        assertThat(DumJobOccupationDictionary.rules().stream().map(DumJobOccupationDictionary.Rule::code)
                .filter(Objects::nonNull)).allSatisfy(code -> assertThat(CBO.table()).containsKey(code));
    }
}
