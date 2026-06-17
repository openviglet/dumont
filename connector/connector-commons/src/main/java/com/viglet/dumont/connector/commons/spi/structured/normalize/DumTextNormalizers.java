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

package com.viglet.dumont.connector.commons.spi.structured.normalize;

import java.text.Normalizer;
import java.util.Optional;

import com.viglet.dumont.commons.utils.DumCommonsUtils;

/**
 * Stateless text normalizers shared across structured sources (roadmap <strong>D26</strong>).
 *
 * <p>
 * Each method is a pure {@code String -> Optional<String>} function, so it plugs straight into a
 * {@link com.viglet.dumont.connector.commons.spi.structured.enrich.DumNormalizationEnricher} (whose
 * mapping is exactly that shape) and obeys the grounding contract by construction: a blank or
 * un-normalizable input returns {@link Optional#empty()} rather than a fabricated value.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumTextNormalizers {

    private DumTextNormalizers() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * The first letter of {@code value}, accent-folded and upper-cased — the alphabetical index key
     * used to bucket entries (e.g. teacher names) by letter. {@code "José Silva" -> "J"},
     * {@code "Ávila" -> "A"}. Returns {@link Optional#empty()} for a blank value or one whose first
     * non-blank character is not a letter.
     */
    public static Optional<String> firstLetter(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String folded = stripAccents(value.trim()).toUpperCase();
        char first = folded.charAt(0);
        return Character.isLetter(first) ? Optional.of(String.valueOf(first)) : Optional.empty();
    }

    /**
     * The plain-text rendering of an HTML fragment — tags stripped, entities decoded — sharing the
     * connector's single {@code Jsoup}-backed implementation. Returns {@link Optional#empty()} when
     * the input is blank or strips down to nothing.
     */
    public static Optional<String> htmlToText(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String text = DumCommonsUtils.html2Text(value);
        return text == null || text.isBlank() ? Optional.empty() : Optional.of(text);
    }

    /** Decompose to NFD and drop the combining marks — locale-independent accent folding. */
    private static String stripAccents(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }
}
