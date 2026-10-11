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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;

/**
 * Reads a salary written as free text — {@code "R$ 2.500 a R$ 3.000 por mês"}, {@code "3k"},
 * {@code "R$ 15/hora"}, {@code "a combinar"} — into monthly BRL (trabalharei <strong>TRA21</strong>).
 * Pure and static.
 *
 * <p>
 * <strong>Amounts.</strong> The first amount, or the first range of two amounts joined by {@code -},
 * {@code a}, {@code até} or {@code e} ({@code "entre R$ 2.000 e R$ 3.000"}). Brazilian
 * ({@code 2.500,00}) and English ({@code 2,500.00}) separators are both read: a separator followed by
 * exactly three digits groups thousands, one followed by one or two digits is the decimal point.
 * {@code k} and {@code mil} multiply by 1,000; in {@code "3 a 5 mil"} the multiplier also applies to
 * the first amount. {@code "até X"} states only the maximum; {@code "a partir de X"}, {@code "acima de
 * X"} and {@code "mais de X"} only the minimum. Text after {@code +} or {@code ;} ({@code "+ comissão
 * de R$ 500"}) is never read.
 *
 * <p>
 * <strong>Currency.</strong> BRL is assumed. An amount with no {@code R$}, {@code BRL} or
 * {@code reais} and no {@code k}/{@code mil} is only read when it is all the text says
 * ({@code "3.000"}, {@code "3.000 por mês"}), so a year or a working-hours figure in a sentence is not
 * a salary. A foreign currency ({@code US$}, {@code €}, ...) is not converted: unparsed.
 *
 * <p>
 * <strong>Period and conversion to a month.</strong> The period is read from the words around the
 * amount (same {@code +}/{@code ;} segment); plural forms ({@code "44 horas semanais"}) describe
 * working time, not the pay period, and are ignored. No period word means monthly, the way Brazilian
 * postings quote pay. The factors:
 * <ul>
 * <li>hour &times; {@value #HOURS_PER_MONTH} — the CLT monthly divisor for a 44-hour week;</li>
 * <li>day &times; {@value #WORKDAYS_PER_MONTH} — working days in a month;</li>
 * <li>week &times; {@value #WEEKS_PER_MONTH} — 52 weeks over 12 months;</li>
 * <li>year &divide; {@value #MONTHS_PER_YEAR}.</li>
 * </ul>
 * The original period is kept as {@code hora}, {@code dia}, {@code mes} or {@code ano}. A weekly
 * amount has no {@code salaryPeriod} value in the site's vocabulary, so it is converted and its
 * period is left absent. Two different period words in one segment leave the text unparsed.
 *
 * <p>
 * <strong>Plausibility.</strong> A monthly value below {@value #MIN_MONTHLY} or above
 * {@value #MAX_MONTHLY}, or a maximum below the minimum, means the text was misread (an hourly rate
 * with no period word, a typo): unparsed, never published.
 *
 * <p>
 * The band ({@link DumJobNormalizer#salaryBand(Double)}) follows the monthly minimum, so a text that
 * only states a maximum has no band.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumJobSalaryParser {

    /** Paid hours in a month: the CLT divisor for a 44-hour week. */
    public static final int HOURS_PER_MONTH = 220;
    /** Working days in a month. */
    public static final int WORKDAYS_PER_MONTH = 22;
    /** Weeks in a month: 52 / 12, rounded. */
    public static final double WEEKS_PER_MONTH = 4.33;
    /** Months in a year. */
    public static final int MONTHS_PER_YEAR = 12;
    /** Lowest credible monthly amount. */
    public static final double MIN_MONTHLY = 400;
    /** Highest credible monthly amount. */
    public static final double MAX_MONTHLY = 200_000;

    /** The {@code salaryPeriod} vocabulary (trabalharei {@code SALARY_PERIODS}). */
    public static final Set<String> PERIODS = Set.of("hora", "dia", "mes", "ano");

    /** The pay period a text states. */
    public enum Period {
        HOUR("hora", HOURS_PER_MONTH, Pattern.compile("\\bhora\\b|/\\s*h\\b|\\bp/\\s*h\\b")),
        DAY("dia", WORKDAYS_PER_MONTH, Pattern.compile("\\b(dia|diaria|diario)\\b")),
        WEEK(null, WEEKS_PER_MONTH, Pattern.compile("\\b(semana|semanal)\\b")),
        MONTH("mes", 1, Pattern.compile("\\b(mes|mensal|mensais|mensalmente)\\b")),
        YEAR("ano", 1.0 / MONTHS_PER_YEAR, Pattern.compile("\\b(ano|anual|anuais)\\b|\\ba\\.a\\.?"));

        private final String value;
        private final double toMonth;
        private final Pattern words;

        Period(String value, double toMonth, Pattern words) {
            this.value = value;
            this.toMonth = toMonth;
            this.words = words;
        }

        /** The {@code salaryPeriod} value, or {@code null} when the vocabulary has none (week). */
        public String value() {
            return value;
        }
    }

    /** How a text was read. */
    public enum Status {
        /** A salary was read. */
        PARSED,
        /** The text states no amount ("a combinar", "salário compatível", empty). */
        NOT_STATED,
        /** The text has an amount the parser could not read with confidence. */
        UNPARSED
    }

    /** Monthly BRL values, the period the text stated, and the band of the minimum. */
    public record Salary(Double min, Double max, String period, String band) {
    }

    /** The outcome: {@link #salary()} is non-null only when {@link #status()} is PARSED. */
    public record Result(Status status, Salary salary) {
        static final Result NOT_STATED = new Result(Status.NOT_STATED, null);
        static final Result UNPARSED = new Result(Status.UNPARSED, null);
    }

    private static final String NUMBER = "\\d{1,3}(?:[.,]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?";
    private static final String AMOUNT = "(" + NUMBER + ")\\s*(k|mil)?\\b";
    private static final String CURRENCY = "(?:r\\$|brl)\\s*";
    /** The first amount or range: optional lead word, amount, optional connector and second amount. */
    private static final Pattern CORE = Pattern.compile(
            "(?:\\b(ate|a partir de|acima de|mais de|desde|entre)\\s+(?:de\\s+)?)?" + "(?:" + CURRENCY
                    + ")?" + AMOUNT + "(?:\\s*(-|–|\\ba\\b|\\bate\\b|\\be\\b)\\s*(?:" + CURRENCY + ")?"
                    + AMOUNT + ")?");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern SEGMENTS = Pattern.compile("[+;]");
    private static final Pattern BRL = Pattern.compile("r\\$|\\bbrl\\b|\\breais\\b");
    private static final Pattern FOREIGN = Pattern.compile(
            "us\\$|u\\$s|\\busd\\b|€|\\beur\\b|\\beuros?\\b|£|\\bgbp\\b|\\bdolar(es)?\\b|\\bdollars?\\b");
    /** What may surround a bare amount (no currency, no multiplier) for it to be read. */
    private static final Pattern BARE_FILLER = Pattern.compile(
            "\\b(salario|remuneracao|valor|bruto|brutos|liquido|fixo|base|por|ao|a|o|de|em|hora|dia|"
                    + "diaria|diario|semana|semanal|mes|mensal|mensais|mensalmente|ano|anual|anuais|h)\\b"
                    + "|a\\.a\\.?|[\\s:.,/()\\-–]");

    private DumJobSalaryParser() {
    }

    /** Read {@code text}; never {@code null}. */
    public static Result parse(String text) {
        String folded = DumJobNormalizer.fold(text);
        if (!DIGIT.matcher(folded).find()) {
            return Result.NOT_STATED;
        }
        if (FOREIGN.matcher(folded).find()) {
            return Result.UNPARSED;
        }
        String segment = firstSegmentWithDigits(folded);
        Matcher core = CORE.matcher(segment);
        if (!core.find()) {
            return Result.UNPARSED;
        }
        String lead = core.group(1);
        String firstMultiplier = core.group(3);
        // "e" joins a range only after "entre": "R$ 2.000 e 13º" is one amount and a bonus.
        boolean range = core.group(5) != null && (!"e".equals(core.group(4)) || "entre".equals(lead));
        String secondMultiplier = range ? core.group(6) : null;
        Double first = amount(core.group(2), firstMultiplier);
        Double second = range ? amount(core.group(5), secondMultiplier) : null;
        if (first == null || range && second == null) {
            return Result.UNPARSED;
        }
        if (firstMultiplier == null && secondMultiplier != null && first < 1_000) {
            // "3 a 5 mil": the multiplier names both ends.
            first = first * 1_000;
        }
        int end = range ? core.end() : core.end(3) >= 0 ? core.end(3) : core.end(2);
        boolean multiplied = firstMultiplier != null || secondMultiplier != null;
        if (!multiplied && !BRL.matcher(segment).find()
                && !BARE_FILLER.matcher(segment.substring(0, core.start()) + segment.substring(end))
                        .replaceAll("").isEmpty()) {
            return Result.UNPARSED;
        }
        Period period = period(segment);
        if (period == null) {
            return Result.UNPARSED;
        }
        Double min = first;
        Double max = second;
        if (!range && "ate".equals(lead)) {
            min = null;
            max = first;
        }
        Double monthlyMin = monthly(min, period);
        Double monthlyMax = monthly(max, period);
        if (!plausible(monthlyMin) || !plausible(monthlyMax)
                || monthlyMin != null && monthlyMax != null && monthlyMax < monthlyMin) {
            return Result.UNPARSED;
        }
        return new Result(Status.PARSED, new Salary(monthlyMin, monthlyMax, period.value(),
                DumJobNormalizer.salaryBand(monthlyMin)));
    }

    /** {@code amount} in {@code period} as a monthly amount, rounded to cents. */
    public static double toMonthly(double amount, Period period) {
        return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(period.toMonth))
                .setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static Double monthly(Double amount, Period period) {
        return amount == null ? null : toMonthly(amount, period);
    }

    private static boolean plausible(Double monthly) {
        return monthly == null || monthly >= MIN_MONTHLY && monthly <= MAX_MONTHLY;
    }

    /** The first {@code +}/{@code ;} segment holding a digit. */
    private static String firstSegmentWithDigits(String folded) {
        for (String segment : SEGMENTS.split(folded)) {
            if (DIGIT.matcher(segment).find()) {
                return segment;
            }
        }
        return null;
    }

    /** The one period the segment names, MONTH when it names none, {@code null} when it names two. */
    private static Period period(String segment) {
        Set<Period> named = EnumSet.noneOf(Period.class);
        for (Period period : Period.values()) {
            if (period.words.matcher(segment).find()) {
                named.add(period);
            }
        }
        if (named.isEmpty()) {
            return Period.MONTH;
        }
        return named.size() == 1 ? named.iterator().next() : null;
    }

    /** {@code 2.500,50} &rarr; 2500.5, {@code 2,500.50} &rarr; 2500.5, {@code 2,5} + k &rarr; 2500. */
    static Double amount(String number, String multiplier) {
        if (number == null) {
            return null;
        }
        String plain = number;
        int last = Math.max(plain.lastIndexOf('.'), plain.lastIndexOf(','));
        if (last >= 0) {
            int decimals = plain.length() - last - 1;
            String whole = plain.substring(0, last).replace(".", "").replace(",", "");
            String tail = plain.substring(last + 1);
            plain = decimals == 3 ? whole + tail : whole + "." + tail;
        }
        try {
            double value = new BigDecimal(plain).doubleValue();
            if (multiplier != null) {
                value = value * 1_000;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code value} when it is one of the {@link #PERIODS}, folded; else {@code null}. */
    public static String periodValue(Object value) {
        if (value == null) {
            return null;
        }
        String folded = DumJobNormalizer.fold(value.toString()).toLowerCase(Locale.ROOT);
        return PERIODS.contains(folded) ? folded : null;
    }
}
