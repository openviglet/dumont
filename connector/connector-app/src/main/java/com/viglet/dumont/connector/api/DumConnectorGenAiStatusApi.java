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

package com.viglet.dumont.connector.api;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * Surfaces whether a target Turing SN site has RAG (GenAI) enabled — roadmap <strong>D17</strong>.
 *
 * <p>
 * A connector source's documents are embedded at Turing's index time only when the bound SN site
 * has RAG fully wired (enabled agent + LLM + embedding model + vector store). When the site is not
 * RAG-ready the documents land in the keyword index only, silently — there is no error. This proxy
 * lets the Dumont UI flag that case by reusing Turing's existing config-only check
 * {@code GET /api/sn/{siteName}/chat/enabled} (no Turing change, same {@code Key} auth as every
 * other outbound call). See {@code docs/EMBEDDING_PARTITION_CONTRACT.md}.
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v2/connector/genai-status")
@Tag(name = "GenAI Status", description = "Whether a Turing SN site has RAG enabled")
@Slf4j
public class DumConnectorGenAiStatusApi {

    private final RestClient restClient;

    public DumConnectorGenAiStatusApi(RestClient restClient) {
        this.restClient = restClient;
    }

    /** RAG readiness of a single SN site, as shown by the Dumont source UI. */
    public record GenAiStatus(String site, boolean ragEnabled, String reason) {
    }

    /** The slice of Turing's {@code ChatEnabledResponse} this proxy reads. */
    private record TuringChatEnabled(boolean enabled, String reason, String sessionCookieName,
            long sessionTtlSeconds) {
    }

    @Operation(summary = "Whether the given Turing SN site has RAG enabled")
    @GetMapping("/{siteName}")
    public GenAiStatus status(@PathVariable String siteName) {
        try {
            TuringChatEnabled response = restClient.get()
                    .uri("/api/sn/{siteName}/chat/enabled", siteName)
                    .retrieve()
                    .body(TuringChatEnabled.class);
            if (response == null) {
                return new GenAiStatus(siteName, false, "UNREACHABLE");
            }
            return new GenAiStatus(siteName, response.enabled(), response.reason());
        } catch (Exception e) {
            // Never claim RAG is enabled when the answer is unknown — degrade to "unreachable"
            // so the UI shows an honest unknown/disabled state rather than a misleading green.
            log.warn("Failed to read RAG status of SN site '{}': {}", siteName, e.getMessage());
            return new GenAiStatus(siteName, false, "UNREACHABLE");
        }
    }
}
