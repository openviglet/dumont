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

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The seam between a {@link DumSummaryEnricher} and whatever writes a short summary of a text,
 * usually an LLM (roadmap <strong>D131</strong>): the prose sibling of {@link DumExtractor}.
 *
 * <p>
 * <strong>Grounding contract.</strong> Prose cannot be matched against the input the way a list
 * item can, so {@link #grounded} checks what a summary must not invent: every number and every
 * capitalised word past a sentence's first must occur in the input, and at least a share of its
 * remaining words must too. A summary failing any of these is dropped whole, never repaired. A
 * blank answer means the summarizer found nothing to say, which is cached. A call that could not
 * be made throws, and nothing is cached.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@FunctionalInterface
public interface DumSummarizer {

    /** The share of a summary's other words that must occur in the input, unless a plugin sets one. */
    double DEFAULT_MIN_GROUNDED_SHARE = 0.6;

    /** Words shorter than this carry no claim ("the", "and", "de") and are not counted. */
    int CONTENT_WORD = 4;

    /** How many leading letters two words must share to count as one, so "requires" grounds "required". */
    int STEM = 5;

    Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    Pattern SENTENCE_END = Pattern.compile("[.!?](?=\\s|$)");

    /** A summary of {@code input}, possibly blank. A thrown exception means the call failed. */
    String summarize(String input);

    /**
     * {@code summary} with its whitespace folded, cut to whole sentences within {@code maxChars},
     * when it is grounded in {@code input}; otherwise empty. A summary longer than the cap with no
     * sentence end inside it is empty, since cutting it mid-sentence would publish a fragment.
     */
    static Optional<String> grounded(String summary, String input, int maxChars, double minShare) {
        if (summary == null || summary.isBlank() || input == null || input.isBlank()) {
            return Optional.empty();
        }
        String text = summary.strip().replaceAll("\\s+", " ");
        if (text.length() > maxChars) {
            int cut = -1;
            Matcher end = SENTENCE_END.matcher(text);
            while (end.find() && end.end() <= maxChars) {
                cut = end.end();
            }
            if (cut < 0) {
                return Optional.empty();
            }
            text = text.substring(0, cut);
        }
        return isGrounded(text, input, minShare) ? Optional.of(text) : Optional.empty();
    }

    private static boolean isGrounded(String summary, String input, double minShare) {
        Set<String> words = new HashSet<>();
        Set<String> stems = new HashSet<>();
        for (Matcher m = WORD.matcher(input); m.find();) {
            String word = m.group().toLowerCase(Locale.ROOT);
            words.add(word);
            stems.add(stem(word));
        }
        int counted = 0;
        int found = 0;
        boolean sentenceStart = true;
        int last = 0;
        for (Matcher m = WORD.matcher(summary); m.find();) {
            if (SENTENCE_END.matcher(summary.substring(last, m.start())).find()) {
                sentenceStart = true;
            }
            last = m.end();
            String original = m.group();
            String word = original.toLowerCase(Locale.ROOT);
            boolean strict = word.chars().anyMatch(Character::isDigit)
                    || (!sentenceStart && Character.isUpperCase(original.charAt(0)));
            sentenceStart = false;
            if (strict) {
                // A number or a name the input does not hold is an invented fact.
                if (!words.contains(word)) {
                    return false;
                }
            } else if (word.length() >= CONTENT_WORD) {
                counted++;
                if (stems.contains(stem(word))) {
                    found++;
                }
            }
        }
        return counted == 0 || found >= minShare * counted;
    }

    private static String stem(String word) {
        return word.length() > STEM ? word.substring(0, STEM) : word;
    }
}
