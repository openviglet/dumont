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

package com.viglet.dumont.connector.commons.spi.structured.enrich;

import java.util.Collection;
import java.util.Optional;

/**
 * The seam between the {@link DumClassificationEnricher} and whatever actually produces a label —
 * usually an LLM (roadmap <strong>D04</strong>).
 *
 * <p>
 * Keeping classification behind this one-method interface means {@code connector-commons} carries no
 * model wiring. The production implementation lives in {@code connector-app} and is a thin adapter
 * over Turing's existing LLM surface ({@code POST /api/v2/summary} via the shared {@code RestClient}
 * — the same path as {@code DumConnectorSummaryApi}); tests substitute a deterministic fake.
 *
 * <p>
 * <strong>Grounding contract.</strong> A classifier returns {@link Optional#empty()} whenever it
 * cannot confidently assign a label (the input is too thin, the model is unsure, the call failed).
 * It must never return an invented label — an empty result means the field is omitted, not filled.
 *
 * <p>
 * <strong>Empty versus thrown (D107).</strong> Empty means the classifier answered and named no
 * label, which {@link DumClassificationEnricher} caches. A call that could not be made or did not
 * complete throws instead: the field is still omitted, but nothing is cached, so the input is asked
 * again on a later run rather than read as "no label" for good.
 *
 * <p>
 * <strong>Closed label set (D107).</strong> {@link #classify(String, Collection)} answers only a
 * label of the set. Its default asks {@link #classify(String)} and checks the answer with
 * {@link #match}; an implementation that can hand the set to its model overrides it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@FunctionalInterface
public interface DumClassifier {

    /**
     * Classify {@code input} into a single label, or {@link Optional#empty()} when no label can be
     * grounded. Implementations should not throw for the "no label" case (return empty); a thrown
     * exception means the call failed, which {@link DumClassificationEnricher} omits without caching.
     */
    Optional<String> classify(String input);

    /**
     * Classify {@code input} into one label of {@code labels}, or {@link Optional#empty()}. An answer
     * outside the set is empty, never published: the label returned is always the set's own spelling.
     */
    default Optional<String> classify(String input, Collection<String> labels) {
        return classify(input).flatMap(answer -> match(answer, labels));
    }

    /**
     * The label of {@code labels} that {@code answer} names: an exact match first, then one that
     * differs only in case, surrounding whitespace, quotes or a final full stop. Empty when the answer
     * names none of them.
     */
    static Optional<String> match(String answer, Collection<String> labels) {
        if (answer == null || labels == null) {
            return Optional.empty();
        }
        String stripped = answer.strip();
        if (labels.contains(stripped)) {
            return Optional.of(stripped);
        }
        String loose = loosen(stripped);
        if (loose.isEmpty()) {
            return Optional.empty();
        }
        return labels.stream().filter(label -> label != null && loosen(label).equalsIgnoreCase(loose))
                .findFirst();
    }

    private static String loosen(String text) {
        String value = text.strip();
        while (!value.isEmpty() && "\"'`.".indexOf(value.charAt(value.length() - 1)) >= 0) {
            value = value.substring(0, value.length() - 1).strip();
        }
        while (!value.isEmpty() && "\"'`".indexOf(value.charAt(0)) >= 0) {
            value = value.substring(1).strip();
        }
        return value;
    }
}
