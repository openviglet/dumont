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
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@FunctionalInterface
public interface DumClassifier {

    /**
     * Classify {@code input} into a single label, or {@link Optional#empty()} when no label can be
     * grounded. Implementations should not throw for the "no label" case (return empty); a thrown
     * exception is treated as "no label" by {@link DumClassificationEnricher}.
     */
    Optional<String> classify(String input);
}
