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

package com.viglet.dumont.connector.commons.spi.structured.infer;

import java.util.Optional;

/**
 * The seam between {@link DumSourceInferrer} and whatever actually runs the language model (roadmap
 * <strong>D13</strong>). Mirrors the role {@code DumClassifier} plays for D04 enrichment: keeping the
 * model call behind a one-method interface means {@code connector-commons} carries <strong>no model
 * wiring</strong>.
 *
 * <p>
 * The production implementation lives in {@code connector-app} and is a thin adapter over Turing's
 * existing LLM surface ({@code POST /api/v2/summary} via the shared {@code RestClient}) — the same
 * path {@code DumConnectorSummaryApi} and {@code DumTuringClassifier} use. Tests substitute a
 * deterministic fake that returns canned JSON.
 *
 * <p>
 * <strong>Grounding contract.</strong> The model returns the raw completion (expected to be a JSON
 * object describing the source structure), or {@link Optional#empty()} when no answer can be
 * obtained (the model is unconfigured, the call failed). An empty result makes the inferrer return an
 * {@link DumSourceShape#UNKNOWN} draft — inference declines rather than fabricating a structure.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.2
 */
@FunctionalInterface
public interface DumSourceInferenceModel {

    /**
     * Run the inference prompt and return the model's raw completion, or {@link Optional#empty()} on
     * any failure. Implementations should not throw for the failure case — an empty result is the
     * grounded "no answer" signal.
     *
     * @param systemPrompt the instruction + output contract
     * @param sample       the source sample (markup excerpt + computed structural signals)
     */
    Optional<String> infer(String systemPrompt, String sample);
}
