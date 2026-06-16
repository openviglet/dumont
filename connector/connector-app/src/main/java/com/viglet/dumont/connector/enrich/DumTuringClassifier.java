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

package com.viglet.dumont.connector.enrich;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.commons.spi.structured.enrich.DumClassifier;
import com.viglet.dumont.connector.config.DumTuringCsrfService;

import lombok.extern.slf4j.Slf4j;

/**
 * The production {@link DumClassifier} for the D04 LLM enrichment path: a thin adapter over Turing's
 * existing LLM surface.
 *
 * <p>
 * It reuses the very same call {@code DumConnectorSummaryApi} makes — {@code POST /api/v2/summary}
 * over the shared {@code RestClient} (base URL + {@code Key} header) with a CSRF token — so there is
 * <strong>no new model wiring in Dumont</strong>: Turing owns the model, the prompt cache
 * ({@code TurLlmCacheService}) and the Global Settings. The classifier is constructed with a
 * classification {@code systemPrompt} (the instruction + the allowed label set) and returns the
 * model's answer trimmed to a single label.
 *
 * <p>
 * <strong>Grounding contract.</strong> Any failure — an unreachable Turing, a non-success response,
 * an empty or {@code "NONE"} answer — yields {@link Optional#empty()}, so the
 * {@code DumClassificationEnricher} omits the field rather than fabricating a label. This is a
 * classifier of <em>structure</em> (which category), never a producer of sourced content.
 *
 * <p>
 * It carries no Spring stereotype: a structured connector instantiates it (with the {@code Key}-
 * bearing {@code RestClient} bean and a source-specific {@code systemPrompt}) and hands it to a
 * {@code DumClassificationEnricher}, which is itself gated by {@code dumont.enrichment.llm.enabled}.
 * This is the seam a native structured connector (D10/D11) and the index-time embedding path (D17)
 * plug into.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public class DumTuringClassifier implements DumClassifier {

    private static final String NO_LABEL = "NONE";

    private final RestClient restClient;
    private final DumTuringCsrfService csrfService;
    private final String systemPrompt;
    private final String cacheKeyPrefix;

    public DumTuringClassifier(RestClient restClient, DumTuringCsrfService csrfService,
            String systemPrompt, String cacheKeyPrefix) {
        this.restClient = restClient;
        this.csrfService = csrfService;
        this.systemPrompt = systemPrompt;
        this.cacheKeyPrefix = cacheKeyPrefix;
    }

    @Override
    public Optional<String> classify(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("cacheKey", cacheKeyPrefix + ":" + Integer.toHexString(input.hashCode()));
            request.put("data", input);
            request.put("systemPrompt", systemPrompt);

            var csrf = csrfService.fetch();

            SummaryResponse response = restClient.post()
                    .uri("/api/v2/summary")
                    .header(csrf.headerName(), csrf.token())
                    .body(request)
                    .retrieve()
                    .body(SummaryResponse.class);

            return toLabel(response);
        } catch (RuntimeException e) {
            log.warn("LLM classification call to Turing failed — omitting the field: {}",
                    e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> toLabel(SummaryResponse response) {
        if (response == null || !response.success() || response.content() == null) {
            return Optional.empty();
        }
        String label = response.content().strip();
        if (label.isBlank() || label.equalsIgnoreCase(NO_LABEL)) {
            return Optional.empty();
        }
        return Optional.of(label);
    }

    /** Mirrors {@code DumConnectorSummaryApi.SummaryResponse} — the Turing summary endpoint shape. */
    public record SummaryResponse(boolean success, String error, String content,
            boolean canRegenerate) {
    }
}
