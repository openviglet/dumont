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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;

/**
 * The contract kinds a piece of text names (trabalharei <strong>TRA21</strong>), in the site's
 * vocabulary ({@code CONTRACTS}). Pure and static.
 *
 * <p>
 * Text is folded first (lower case, accents stripped), and every keyword matches on word boundaries,
 * so {@code "efetivação"} is not {@code efetivo} and {@code "aprendizado"} is not {@code aprendiz}.
 * Only singular forms count: a plural names the people a role deals with, not the role's contract
 * ({@code "Supervisor de Estagiários"}, {@code "Instrutor de Aprendizes"}).
 *
 * <ul>
 * <li>{@code clt}: CLT, efetivo/efetiva, carteira assinada, celetista</li>
 * <li>{@code pj}: PJ, pessoa jurídica</li>
 * <li>{@code estagio}: estágio, estagiário/estagiária</li>
 * <li>{@code temporario}: temporário/temporária</li>
 * <li>{@code aprendiz}: aprendiz (jovem/menor aprendiz)</li>
 * <li>{@code trainee}: trainee</li>
 * <li>{@code freelancer}: freelancer, freelance, freela</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumJobContractParser {

    /** A contract kind and the words that name it. */
    public enum Contract {
        CLT("clt", "\\b(clt|efetiv[oa]|carteira assinada|celetista)\\b"),
        PJ("pj", "\\b(pj|pessoa juridica)\\b"),
        ESTAGIO("estagio", "\\b(estagio|estagiari[oa])\\b"),
        TEMPORARIO("temporario", "\\btemporari[oa]\\b"),
        APRENDIZ("aprendiz", "\\baprendiz\\b"),
        TRAINEE("trainee", "\\btrainee\\b"),
        FREELANCER("freelancer", "\\b(freelancer|freelance|freela)\\b");

        private final String value;
        private final Pattern words;

        Contract(String value, String words) {
            this.value = value;
            this.words = Pattern.compile(words);
        }

        /** The {@code contract} field value. */
        public String value() {
            return value;
        }
    }

    /** The {@code contract} vocabulary. */
    public static final Set<String> VALUES = Set.copyOf(
            Arrays.stream(Contract.values()).map(Contract::value).toList());

    private DumJobContractParser() {
    }

    /** Every contract kind {@code text} names; empty for {@code null} or none. */
    public static Set<Contract> kinds(String text) {
        String folded = DumJobNormalizer.fold(text);
        Set<Contract> named = EnumSet.noneOf(Contract.class);
        if (folded.isEmpty()) {
            return named;
        }
        for (Contract contract : Contract.values()) {
            if (contract.words.matcher(folded).find()) {
                named.add(contract);
            }
        }
        return named;
    }

    /**
     * The contract {@code text} names when it names exactly one kind; empty for none and for two or
     * more (never guessed).
     */
    public static Optional<String> parse(String text) {
        Set<Contract> named = kinds(text);
        return named.size() == 1 ? Optional.of(named.iterator().next().value()) : Optional.empty();
    }
}
