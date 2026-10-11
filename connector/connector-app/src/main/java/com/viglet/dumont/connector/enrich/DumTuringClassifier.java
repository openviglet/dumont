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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.commons.spi.structured.enrich.DumClassificationStore;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumClassifier;
import com.viglet.dumont.connector.config.DumTuringCsrfService;

/**
 * The production {@link DumClassifier} for the D04 LLM enrichment path: a thin adapter over Turing's
 * existing LLM surface.
 *
 * <p>
 * It reuses the very same call {@code DumConnectorSummaryApi} makes — {@code POST /api/v2/summary}
 * over the shared {@code RestClient} (base URL + {@code Key} header) with a CSRF token — so there is
 * <strong>no new model wiring in Dumont</strong>: Turing owns the model, the prompt cache
 * ({@code TurLlmCacheService}) and the Global Settings. The classifier is constructed with a
 * classification {@code systemPrompt} (the instruction) and returns the model's answer trimmed to a
 * single label. {@link #classify(String, Collection)} appends the closed label set to that prompt and
 * accepts only a label of the set (D107). Turing's cache key is the SHA-256 of the prompt and the
 * input (D108), so two inputs never share an answer and a changed label set is a miss.
 *
 * <p>
 * <strong>Grounding contract.</strong> An empty or {@code "NONE"} answer yields
 * {@link Optional#empty()}, so the {@code DumClassificationEnricher} omits the field rather than
 * fabricating a label. A call that fails — an unreachable Turing, a non-success response — throws
 * (D107): the enricher still omits the field, but does not cache the failure as "no label". This is a
 * classifier of <em>structure</em> (which category), never a producer of sourced content.
 *
 * <p>
 * Plugins reach it through the {@code DumClassifierFactory} bean {@link DumLlmClassifierConfig}
 * registers when {@code dumont.enrichment.llm.enabled=true} (D106). The class itself carries no
 * Spring stereotype: a structured connector instantiates it (with the {@code Key}-
 * bearing {@code RestClient} bean and a source-specific {@code systemPrompt}) and hands it to a
 * {@code DumClassificationEnricher}, which is itself gated by {@code dumont.enrichment.llm.enabled}.
 * This is the seam a native structured connector (D10/D11) and the index-time embedding path (D17)
 * plug into.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
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
        return ask(input, systemPrompt);
    }

    @Override
    public Optional<String> classify(String input, Collection<String> labels) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        StringBuilder prompt = new StringBuilder(systemPrompt == null ? "" : systemPrompt)
                .append("\n\nAnswer with exactly one label of this list, written as listed, or ")
                .append(NO_LABEL).append(" when none applies:\n");
        labels.forEach(label -> prompt.append(label).append('\n'));
        return ask(input, prompt.toString()).flatMap(answer -> DumClassifier.match(answer, labels));
    }

    private Optional<String> ask(String input, String prompt) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("cacheKey", cacheKey(prompt, input));
        request.put("data", input);
        request.put("systemPrompt", prompt);

        var csrf = csrfService.fetch();

        SummaryResponse response = restClient.post()
                .uri("/api/v2/summary")
                .header(csrf.headerName(), csrf.token())
                .body(request)
                .retrieve()
                .body(SummaryResponse.class);

        if (response == null || !response.success()) {
            throw new IllegalStateException("Turing could not classify: "
                    + (response == null ? "empty response" : response.error()));
        }
        return toLabel(response);
    }

    /**
     * Turing's cache key for one question: the prefix and the SHA-256 of the prompt and the input
     * (D108). A 32-bit {@code hashCode} let colliding inputs ("Aa" and "BB") read each other's answer.
     */
    String cacheKey(String prompt, String input) {
        return cacheKeyPrefix + ":" + DumClassificationStore.hash(prompt + '\n' + input);
    }

    private Optional<String> toLabel(SummaryResponse response) {
        if (response.content() == null) {
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
