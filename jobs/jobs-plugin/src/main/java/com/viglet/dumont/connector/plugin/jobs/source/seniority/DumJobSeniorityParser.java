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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;

/**
 * The seniority a job title states (trabalharei <strong>TRA21</strong>), in the site's vocabulary
 * ({@code SENIORITIES}). Pure and static; titles only — a description's "experiência com pessoas
 * sêniores" says nothing about the opening.
 *
 * <p>
 * The title is folded (lower case, accents stripped) and split into words. A word counts only when
 * it is the whole token: singular forms only ({@code "Gerentes"}, {@code "Supervisor de Estagiários"}
 * name the people managed), and a word joined to its neighbour by a hyphen without spaces is part of
 * a compound ({@code "Sub-Gerente"}) and does not count.
 *
 * <ul>
 * <li>{@code estagio}: estágio, estagiário/estagiária</li>
 * <li>{@code junior}: júnior, junior, jr</li>
 * <li>{@code pleno}: pleno/plena, pl</li>
 * <li>{@code senior}: sênior, senior, sr</li>
 * <li>{@code especialista}: especialista</li>
 * <li>{@code coordenacao}: coordenador/coordenadora</li>
 * <li>{@code gerencia}: gerente</li>
 * </ul>
 *
 * <p>
 * <strong>The abbreviations {@code jr}, {@code pl} and {@code sr}</strong> (with or without a dot)
 * are also forms of address ("Cuidador para Sr. Idoso") and pieces of other names ("PL/SQL"), so
 * they count only as a qualifier right after the role. The rule:
 * <ol>
 * <li>never the title's first word ("Sr. João contrata");</li>
 * <li>the word before it is not an article, preposition or form of address (de, da, do, para, ao,
 * a, o, com, em, sr, sra, senhor, ...): "Analista Sr", "Dev Sr.", "Analista de Sistemas Sr" count;
 * "Cuidador de Sr", "Atendimento ao Sr(a)" do not;</li>
 * <li>it is not followed by {@code (a)} (the gendered address "Sr(a)") or by a number ("Sr. 80
 * anos");</li>
 * <li>a slash joins it only to another seniority word: "Analista Jr/Pl" reads both (and is then a
 * conflict), "Desenvolvedor PL/SQL" reads neither.</li>
 * </ol>
 *
 * <p>
 * Two different seniorities in one title ({@code "Analista Jr/Pl"}, {@code "Coordenador Sênior"})
 * answer nothing: never guessed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumJobSeniorityParser {

    private static final Map<String, String> WORDS = Map.ofEntries(
            Map.entry("estagio", "estagio"),
            Map.entry("estagiario", "estagio"),
            Map.entry("estagiaria", "estagio"),
            Map.entry("junior", "junior"),
            Map.entry("pleno", "pleno"),
            Map.entry("plena", "pleno"),
            Map.entry("senior", "senior"),
            Map.entry("especialista", "especialista"),
            Map.entry("coordenador", "coordenacao"),
            Map.entry("coordenadora", "coordenacao"),
            Map.entry("gerente", "gerencia"));

    private static final Map<String, String> ABBREVIATIONS = Map.of("jr", "junior", "pl", "pleno",
            "sr", "senior");

    /** Words after which an abbreviation is an address or an object, not the role's level. */
    private static final Set<String> FUNCTION_WORDS = Set.of("a", "o", "as", "os", "ao", "aos", "um",
            "uma", "de", "da", "do", "das", "dos", "d", "para", "pra", "p", "com", "em", "na", "no",
            "nas", "nos", "e", "ou", "sr", "sra", "srta", "senhor", "senhora", "dona", "dom");

    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private record Token(String word, int start, int end) {
    }

    private DumJobSeniorityParser() {
    }

    /** The one seniority {@code title} states; empty for none and for two different ones. */
    public static Optional<String> parse(String title) {
        Set<String> named = levels(title);
        return named.size() == 1 ? Optional.of(named.iterator().next()) : Optional.empty();
    }

    /** Every seniority {@code title} states, by the rules above. */
    public static Set<String> levels(String title) {
        String folded = DumJobNormalizer.fold(title);
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(folded);
        while (matcher.find()) {
            tokens.add(new Token(matcher.group(), matcher.start(), matcher.end()));
        }
        Set<String> named = new HashSet<>();
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (inCompound(folded, token)) {
                continue;
            }
            String level = WORDS.get(token.word());
            if (level == null && countsAsAbbreviation(folded, tokens, i)) {
                level = ABBREVIATIONS.get(token.word());
            }
            if (level != null) {
                named.add(level);
            }
        }
        return named;
    }

    /** Joined to a neighbour by a hyphen with no space: part of a compound word. */
    private static boolean inCompound(String folded, Token token) {
        boolean before = token.start() >= 2 && folded.charAt(token.start() - 1) == '-'
                && Character.isLetterOrDigit(folded.charAt(token.start() - 2));
        boolean after = token.end() + 1 < folded.length() && folded.charAt(token.end()) == '-'
                && Character.isLetterOrDigit(folded.charAt(token.end() + 1));
        return before || after;
    }

    private static boolean countsAsAbbreviation(String folded, List<Token> tokens, int i) {
        Token token = tokens.get(i);
        if (!ABBREVIATIONS.containsKey(token.word()) || i == 0) {
            return false;
        }
        Token previous = tokens.get(i - 1);
        if (FUNCTION_WORDS.contains(previous.word())) {
            return false;
        }
        String rest = folded.substring(token.end());
        if (rest.startsWith("(a)") || rest.startsWith("/a ") || rest.equals("/a")) {
            return false;
        }
        Token next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
        if (next != null && NUMBER.matcher(next.word()).matches()) {
            return false;
        }
        String gapBefore = folded.substring(previous.end(), token.start());
        if (gapBefore.contains("/") && !isLevel(previous.word())) {
            return false;
        }
        String gapAfter = next == null ? "" : folded.substring(token.end(), next.start());
        return !(gapAfter.contains("/") && !isLevel(next.word()));
    }

    private static boolean isLevel(String word) {
        return WORDS.containsKey(word) || ABBREVIATIONS.containsKey(word);
    }
}
