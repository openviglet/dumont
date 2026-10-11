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

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.viglet.dumont.connector.commons.spi.structured.recipe.DumTaxonomy;

/**
 * The closed label set handed to the fallback classifier, and the reading of its answer (trabalharei
 * <strong>TRA22</strong>).
 *
 * <p>
 * The commons {@link com.viglet.dumont.connector.commons.spi.structured.enrich.DumClassifier} takes one
 * input string and carries its instructions itself (Turing's {@code DumTuringClassifier} is built with a
 * {@code systemPrompt}), so the family list travels in {@link #systemPrompt(DumTaxonomy)}: every code
 * and official title, and the rule that the answer is one code from the list or {@code NONE}.
 * {@link #code(String, DumTaxonomy)} then accepts only a code (or a family slug) the taxonomy holds; any
 * other answer is "no family", never a guess.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumJobOccupationLabels {

    /** The answer for "the title names no occupation". */
    public static final String NONE = "NONE";

    private static final Pattern CODE = Pattern.compile("(?<!\\d)\\d{4}(?!\\d)");

    private DumJobOccupationLabels() {
    }

    /** The classification instruction with the whole family list of {@code taxonomy}. */
    public static String systemPrompt(DumTaxonomy taxonomy) {
        StringBuilder prompt = new StringBuilder()
                .append("Você classifica títulos de vagas de emprego no Brasil em uma família da CBO 2002 ")
                .append("(Classificação Brasileira de Ocupações, código de 4 dígitos).\n")
                .append("Responda somente com o código de 4 dígitos de UMA família da lista abaixo, sem ")
                .append("texto adicional.\n")
                .append("Responda ").append(NONE).append(" quando o título não permitir identificar a ")
                .append("ocupação (por exemplo apenas \"Jovem Aprendiz\", \"Estágio\", \"Vaga\" ou uma área ")
                .append("sem cargo) ou quando houver mais de uma família plausível.\n")
                .append("Nunca responda um código que não esteja na lista.\n\n")
                .append("Famílias CBO 2002 (código - título):\n");
        for (Map.Entry<String, String> family : new TreeMap<>(taxonomy.table()).entrySet()) {
            prompt.append(family.getKey()).append(" - ").append(family.getValue()).append('\n');
        }
        return prompt.toString();
    }

    /**
     * The family code an answer names, when {@code taxonomy} holds it: the first 4-digit number in the
     * answer, or an answer that is exactly a family's slug. Empty for {@link #NONE}, blank text, or any
     * code outside the list.
     */
    public static Optional<String> code(String answer, DumTaxonomy taxonomy) {
        if (answer == null || answer.isBlank() || answer.strip().equalsIgnoreCase(NONE)) {
            return Optional.empty();
        }
        Matcher matcher = CODE.matcher(answer);
        if (matcher.find()) {
            String code = matcher.group();
            return taxonomy.table().containsKey(code) ? Optional.of(code) : Optional.empty();
        }
        String slug = DumCboFamilies.slug(answer);
        return taxonomy.table().entrySet().stream()
                .filter(family -> DumCboFamilies.slug(family.getValue()).equals(slug))
                .map(Map.Entry::getKey)
                .findFirst();
    }
}
