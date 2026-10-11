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
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.commons.spi.structured.enrich.DumClassificationStore;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumExtractor;
import com.viglet.dumont.connector.config.DumTuringCsrfService;
import com.viglet.dumont.connector.enrich.DumTuringClassifier.SummaryResponse;

/**
 * The production {@link DumExtractor} (roadmap <strong>D131</strong>): the same Turing call as
 * {@link DumTuringClassifier}, {@code POST /api/v2/summary}, asked for one item per line copied
 * from the text. The answer is split into candidates; grounding them against the input is the
 * enricher's ({@link DumExtractor#grounded}), so a line the model invented never publishes.
 *
 * <p>
 * An empty or {@code NONE} answer is no items. A failed call throws, so nothing is cached.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public class DumTuringExtractor implements DumExtractor {

    private static final String NO_ITEMS = "NONE";
    /** A list marker the model may put before an item: a bullet, or a number and a dot or bracket. */
    private static final Pattern MARKER = Pattern.compile("^(?:[-*•]\\s*|\\d+[.)]\\s*)");

    private final RestClient restClient;
    private final DumTuringCsrfService csrfService;
    private final String systemPrompt;
    private final String cacheKeyPrefix;

    public DumTuringExtractor(RestClient restClient, DumTuringCsrfService csrfService,
            String systemPrompt, String cacheKeyPrefix) {
        this.restClient = restClient;
        this.csrfService = csrfService;
        this.systemPrompt = systemPrompt;
        this.cacheKeyPrefix = cacheKeyPrefix;
    }

    @Override
    public List<String> extract(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        String prompt = (systemPrompt == null ? "" : systemPrompt)
                + "\n\nAnswer with one item per line, each copied exactly as the text writes it, "
                + "and nothing else; answer " + NO_ITEMS + " when the text names none.";
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
            throw new IllegalStateException("Turing could not extract: "
                    + (response == null ? "empty response" : response.error()));
        }
        return candidates(response.content());
    }

    /** The answer's lines, list markers and blank lines dropped; {@code NONE} is no items. */
    static List<String> candidates(String content) {
        if (content == null || content.isBlank() || content.strip().equalsIgnoreCase(NO_ITEMS)) {
            return List.of();
        }
        return content.lines()
                .map(line -> MARKER.matcher(line.strip()).replaceFirst("").strip())
                .filter(line -> !line.isEmpty() && !line.equalsIgnoreCase(NO_ITEMS))
                .toList();
    }
}
