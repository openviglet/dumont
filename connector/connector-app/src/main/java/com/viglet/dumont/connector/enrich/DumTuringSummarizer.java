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

import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.commons.spi.structured.enrich.DumClassificationStore;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumSummarizer;
import com.viglet.dumont.connector.config.DumTuringCsrfService;
import com.viglet.dumont.connector.enrich.DumTuringClassifier.SummaryResponse;

/**
 * The production {@link DumSummarizer} (roadmap <strong>D131</strong>): the same Turing call as
 * {@link DumTuringExtractor}, {@code POST /api/v2/summary}, asked for a summary drawn from the text
 * alone and within the length cap. Checking it against the input is the enricher's
 * ({@link DumSummarizer#grounded}), so a summary naming what the text does not never publishes.
 *
 * <p>
 * An empty or {@code NONE} answer is no summary. A failed call throws, so nothing is cached.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public class DumTuringSummarizer implements DumSummarizer {

    private static final String NO_SUMMARY = "NONE";

    private final RestClient restClient;
    private final DumTuringCsrfService csrfService;
    private final String systemPrompt;
    private final int maxChars;
    private final String cacheKeyPrefix;

    public DumTuringSummarizer(RestClient restClient, DumTuringCsrfService csrfService,
            String systemPrompt, int maxChars, String cacheKeyPrefix) {
        this.restClient = restClient;
        this.csrfService = csrfService;
        this.systemPrompt = systemPrompt;
        this.maxChars = maxChars;
        this.cacheKeyPrefix = cacheKeyPrefix;
    }

    @Override
    public String summarize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String prompt = (systemPrompt == null ? "" : systemPrompt)
                + "\n\nWrite plain prose of at most " + maxChars + " characters, in the text's language, "
                + "using only facts the text states: no number, name or claim it does not hold. "
                + "Answer " + NO_SUMMARY + " when the text gives nothing to summarize.";
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("cacheKey", cacheKeyPrefix + ":" + DumClassificationStore.hash(prompt + '\n' + input));
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
            throw new IllegalStateException("Turing could not summarize: "
                    + (response == null ? "empty response" : response.error()));
        }
        return answer(response.content());
    }

    /** The model's summary, blank for {@code NONE} or nothing. */
    static String answer(String content) {
        if (content == null || content.isBlank() || content.strip().equalsIgnoreCase(NO_SUMMARY)) {
            return "";
        }
        return content.strip();
    }
}
