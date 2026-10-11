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

/**
 * An enricher contribution that says where its value came from (roadmap <strong>D101</strong>):
 * {@code Map.of("contract", DumSourced.of("CLT", "title", 0.8))} sets the field and records
 * provenance {@code enrichment:<enricher>} with selector {@code title} and confidence {@code 0.8},
 * so a reader can tell a value taken from the partner's field from one read out of the text.
 *
 * @param value      the field value, grounded like any contribution
 * @param selector   where the enricher read it: an input field, {@code title}, {@code text}, a
 *                   pattern name; {@code null} when it has none
 * @param confidence a 0&ndash;1 confidence, or {@code null}
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record DumSourced(Object value, String selector, Double confidence) {

    public DumSourced {
        if (confidence != null && (confidence < 0 || confidence > 1)) {
            throw new IllegalArgumentException("A confidence is between 0 and 1, not " + confidence);
        }
    }

    public static DumSourced of(Object value, String selector) {
        return new DumSourced(value, selector, null);
    }

    public static DumSourced of(Object value, String selector, Double confidence) {
        return new DumSourced(value, selector, confidence);
    }
}
