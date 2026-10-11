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

package com.viglet.dumont.connector.plugin.jobs.source.salary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.viglet.dumont.connector.plugin.jobs.source.salary.DumJobSalaryParser.Period;
import com.viglet.dumont.connector.plugin.jobs.source.salary.DumJobSalaryParser.Salary;
import com.viglet.dumont.connector.plugin.jobs.source.salary.DumJobSalaryParser.Status;

class DumJobSalaryParserTest {

    /** text | monthly min | monthly max | salaryPeriod | salaryBand — an empty cell is absent. */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            R$ 2.500 a R$ 3.000 por mês                           | 2500   | 3000  | mes  | 1.5k-3k
            R$ 2500,00                                            | 2500   |       | mes  | 1.5k-3k
            3k                                                    | 3000   |       | mes  | 1.5k-3k
            3 mil                                                 | 3000   |       | mes  | 1.5k-3k
            R$ 15/hora                                            | 3300   |       | hora | 3k-5k
            R$ 120 por dia                                        | 2640   |       | dia  | 1.5k-3k
            R$ 60.000 por ano                                     | 5000   |       | ano  | 3k-5k
            R$ 5.000 - R$ 7.000 por mês                           | 5000   | 7000  | mes  | 3k-5k
            R$ 3.000                                              | 3000   |       | mes  | 1.5k-3k
            R$ 1.412,00 mensal                                    | 1412   |       | mes  | ate-1.5k
            'R$ 2.500,00 a R$ 3.000,00 /mês'                      | 2500   | 3000  | mes  | 1.5k-3k
            R$4.000-R$6.000                                       | 4000   | 6000  | mes  | 3k-5k
            R$ 18 por hora                                        | 3960   |       | hora | 3k-5k
            R$ 150 por dia                                        | 3300   |       | dia  | 3k-5k
            R$ 15,50/h                                            | 3410   |       | hora | 3k-5k
            Diária de R$ 150                                      | 3300   |       | dia  | 3k-5k
            R$ 25 por hora                                        | 5500   |       | hora | 5k-8k
            R$ 800 por semana                                     | 3464   |       |      | 3k-5k
            R$ 60 mil por ano                                     | 5000   |       | ano  | 3k-5k
            R$ 150.000 ao ano                                     | 12500  |       | ano  | 12k+
            R$ 4k - 6k                                            | 4000   | 6000  | mes  | 3k-5k
            3 a 5 mil                                             | 3000   | 5000  | mes  | 1.5k-3k
            '2,5k'                                                | 2500   |       | mes  | 1.5k-3k
            Entre R$ 2.000 e R$ 3.000                             | 2000   | 3000  | mes  | 1.5k-3k
            De R$ 1.800 até R$ 2.200                              | 1800   | 2200  | mes  | 1.5k-3k
            Até R$ 3.000                                          |        | 3000  | mes  |
            A partir de R$ 4.000                                  | 4000   |       | mes  | 3k-5k
            R$ 3.000 + comissão                                   | 3000   |       | mes  | 1.5k-3k
            Salário fixo de R$ 2.000 + comissões de até R$ 5.000  | 2000   |       | mes  | 1.5k-3k
            R$ 3.000 + VR R$ 30/dia                               | 3000   |       | mes  | 1.5k-3k
            R$ 2.000 para 44 horas semanais                       | 2000   |       | mes  | 1.5k-3k
            'R$ 1,412.00'                                         | 1412   |       | mes  | ate-1.5k
            R$ 12.000,00 a R$ 15.000,00                           | 12000  | 15000 | mes  | 8k-12k
            R$ 12.000,01                                          | 12000.01 |     | mes  | 12k+
            3.000                                                 | 3000   |       | mes  | 1.5k-3k
            3.000 por mês                                         | 3000   |       | mes  | 1.5k-3k
            Bolsa auxílio de R$ 1.200                             | 1200   |       | mes  | ate-1.5k
            R$ 2.000 e 13º salário                                | 2000   |       | mes  | 1.5k-3k
            R$ 3.500,00 brutos                                    | 3500   |       | mes  | 3k-5k
            R$ 2.200 mensais                                      | 2200   |       | mes  | 1.5k-3k
            Salário: R$ 1.500,00                                  | 1500   |       | mes  | ate-1.5k
            BRL 8000                                              | 8000   |       | mes  | 5k-8k
            R$ 1.000.000 por ano                                  | 83333.33 |     | ano  | 12k+
            """)
    void readsSalaryText(String text, Double min, Double max, String period, String band) {
        DumJobSalaryParser.Result result = DumJobSalaryParser.parse(text);
        assertThat(result.status()).isEqualTo(Status.PARSED);
        assertThat(result.salary()).isEqualTo(new Salary(min, max, period, band));
    }

    @ParameterizedTest(name = "{0}")
    @NullAndEmptySource
    @ValueSource(strings = { "A combinar", "a combinar", "Salário compatível", "Salário compatível com o mercado",
            "Negociável", "Não informado", "  " })
    void textWithoutAnAmountStatesNothing(String text) {
        assertThat(DumJobSalaryParser.parse(text).status()).isEqualTo(Status.NOT_STATED);
        assertThat(DumJobSalaryParser.parse(text).salary()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            // Implausible as a month: an hourly rate without its period, a typo, a misplaced dot.
            "R$ 25", "R$ 1 por hora", "R$ 300.000", "R$ 3.000.000 por mês",
            // A maximum below the minimum.
            "R$ 5.000 - R$ 3.000",
            // Another currency.
            "US$ 3,000", "€ 2.000 por mês", "USD 4000",
            // Two different periods in one statement.
            "R$ 5.000 por mês ou R$ 60.000 por ano",
            // A bare number inside a sentence is not a salary.
            "Início em 2026", "Vaga para 30 pessoas" })
    void anAmountThatCannotBeReadIsUnparsed(String text) {
        assertThat(DumJobSalaryParser.parse(text).status()).isEqualTo(Status.UNPARSED);
        assertThat(DumJobSalaryParser.parse(text).salary()).isNull();
    }

    @Test
    void conversionFactors() {
        assertThat(DumJobSalaryParser.toMonthly(10, Period.HOUR)).isEqualTo(2200.0);
        assertThat(DumJobSalaryParser.toMonthly(100, Period.DAY)).isEqualTo(2200.0);
        assertThat(DumJobSalaryParser.toMonthly(1000, Period.WEEK)).isEqualTo(4330.0);
        assertThat(DumJobSalaryParser.toMonthly(1000, Period.MONTH)).isEqualTo(1000.0);
        assertThat(DumJobSalaryParser.toMonthly(60000, Period.YEAR)).isEqualTo(5000.0);
        assertThat(DumJobSalaryParser.toMonthly(100000, Period.YEAR)).isEqualTo(8333.33);
    }

    @Test
    void plausibilityBoundsAreInclusive() {
        assertThat(DumJobSalaryParser.parse("R$ 400").status()).isEqualTo(Status.PARSED);
        assertThat(DumJobSalaryParser.parse("R$ 399,99").status()).isEqualTo(Status.UNPARSED);
        assertThat(DumJobSalaryParser.parse("R$ 200.000").status()).isEqualTo(Status.PARSED);
        assertThat(DumJobSalaryParser.parse("R$ 200.000,01").status()).isEqualTo(Status.UNPARSED);
    }

    @Test
    void periodValueKeepsTheVocabularyOnly() {
        assertThat(DumJobSalaryParser.periodValue("mes")).isEqualTo("mes");
        assertThat(DumJobSalaryParser.periodValue("Mês")).isEqualTo("mes");
        assertThat(DumJobSalaryParser.periodValue("ano")).isEqualTo("ano");
        assertThat(DumJobSalaryParser.periodValue("semana")).isNull();
        assertThat(DumJobSalaryParser.periodValue(null)).isNull();
    }
}
