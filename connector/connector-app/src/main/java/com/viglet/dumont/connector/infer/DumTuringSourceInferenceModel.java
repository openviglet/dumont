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

package com.viglet.dumont.connector.infer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.commons.spi.structured.infer.DumSourceInferenceModel;
import com.viglet.dumont.connector.config.DumTuringCsrfService;

import lombok.extern.slf4j.Slf4j;

/**
 * The production {@link DumSourceInferenceModel} for D13 source inference: a thin adapter over
 * Turing's existing LLM surface.
 *
 * <p>
 * It reuses the very same call {@code DumConnectorSummaryApi} and {@code DumTuringClassifier} make —
 * {@code POST /api/v2/summary} over the shared {@code RestClient} ({@code turing.url} base URL +
 * {@code Key} header) with a CSRF token — so there is <strong>no new model wiring in Dumont</strong>:
 * Turing owns the model, the prompt cache ({@code TurLlmCacheService}) and the Global Settings.
 *
 * <p>
 * <strong>Grounding contract.</strong> Any failure (unreachable Turing, a non-success response, an
 * empty answer) yields {@link Optional#empty()}, so {@code DumSourceInferrer} returns an
 * {@code UNKNOWN} draft rather than guessing a structure.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.2
 */
@Slf4j
public class DumTuringSourceInferenceModel implements DumSourceInferenceModel {

    private final RestClient restClient;
    private final DumTuringCsrfService csrfService;

    public DumTuringSourceInferenceModel(RestClient restClient, DumTuringCsrfService csrfService) {
        this.restClient = restClient;
        this.csrfService = csrfService;
    }

    @Override
    public Optional<String> infer(String systemPrompt, String sample) {
        if (sample == null || sample.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("cacheKey", "dumont-infer:" + Integer.toHexString((systemPrompt + sample).hashCode()));
            request.put("data", sample);
            request.put("systemPrompt", systemPrompt);

            var csrf = csrfService.fetch();

            SummaryResponse response = restClient.post()
                    .uri("/api/v2/summary")
                    .header(csrf.headerName(), csrf.token())
                    .body(request)
                    .retrieve()
                    .body(SummaryResponse.class);

            return toCompletion(response);
        } catch (RuntimeException e) {
            log.warn("Source inference call to Turing failed — declining with UNKNOWN: {}",
                    e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> toCompletion(SummaryResponse response) {
        if (response == null || !response.success() || response.content() == null
                || response.content().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(response.content());
    }

    /** Mirrors {@code DumConnectorSummaryApi.SummaryResponse} — the Turing summary endpoint shape. */
    public record SummaryResponse(boolean success, String error, String content,
            boolean canRegenerate) {
    }
}
