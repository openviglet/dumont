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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The seam between a {@link DumExtractionEnricher} and whatever lists the values a text holds,
 * usually an LLM (roadmap <strong>D131</strong>): the open-vocabulary sibling of
 * {@link DumClassifier}, which answers one label of a closed set.
 *
 * <p>
 * <strong>Grounding contract.</strong> An extractor answers candidates; only those that occur in
 * the input survive {@link #grounded}, spelled as the input spells them, so an item the model
 * invented is dropped rather than published. An empty list means the extractor answered and found
 * nothing, which is cached. A call that could not be made throws, and nothing is cached.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@FunctionalInterface
public interface DumExtractor {

    /**
     * The items the model names in {@code input}, as it spelled them, possibly empty. A thrown
     * exception means the call failed.
     */
    List<String> extract(String input);

    /**
     * The {@code candidates} that occur in {@code input}, each as the input spells it, in the order
     * given and without repeats. Case and runs of whitespace are ignored when looking; the answer
     * keeps the input's case, with each whitespace run written as one space.
     */
    static List<String> grounded(List<String> candidates, String input) {
        if (candidates == null || candidates.isEmpty() || input == null || input.isBlank()) {
            return List.of();
        }
        Folded text = Folded.of(input);
        Map<String, String> found = new LinkedHashMap<>();
        for (String candidate : candidates) {
            String needle = candidate == null ? "" : Folded.of(candidate.strip()).text();
            int at = needle.isEmpty() ? -1 : wholeWordIndex(text.text(), needle);
            if (at >= 0) {
                found.putIfAbsent(needle, text.original(at, at + needle.length()));
            }
        }
        return List.copyOf(found.values());
    }

    /**
     * Where {@code needle} first occurs in {@code text} as whole words, or {@code -1}: "Java" does
     * not ground in "JavaScript".
     */
    private static int wholeWordIndex(String text, String needle) {
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            int end = at + needle.length();
            boolean startsWord = at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1));
            boolean endsWord = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (startsWord && endsWord) {
                return at;
            }
        }
        return -1;
    }

    /**
     * {@code text} lower-cased with each run of whitespace folded to one space, keeping for every
     * folded character the position it came from, so a match is answered in the original spelling.
     */
    record Folded(String text, String source, List<Integer> origin) {

        static Folded of(String source) {
            StringBuilder out = new StringBuilder();
            List<Integer> positions = new ArrayList<>();
            boolean inSpace = false;
            for (int i = 0; i < source.length(); i++) {
                char c = source.charAt(i);
                if (Character.isWhitespace(c)) {
                    if (!inSpace && !out.isEmpty()) {
                        out.append(' ');
                        positions.add(i);
                    }
                    inSpace = true;
                } else {
                    out.append(Character.toLowerCase(c));
                    positions.add(i);
                    inSpace = false;
                }
            }
            // Lower-cased per char above, never per string: a string's lower case can change its
            // length, and every folded char must keep the position it came from.
            return new Folded(out.toString(), source, List.copyOf(positions));
        }

        /** The input's own spelling of a match, its case kept and each whitespace run one space. */
        String original(int from, int to) {
            return source.substring(origin.get(from), origin.get(to - 1) + 1).replaceAll("\\s+", " ");
        }
    }
}
