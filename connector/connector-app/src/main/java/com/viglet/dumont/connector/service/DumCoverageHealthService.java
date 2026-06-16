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

package com.viglet.dumont.connector.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.api.DumConnectorSummaryApi.SummaryResponse;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageDrift;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageMonitor;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageSnapshot;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumFieldCoverage;
import com.viglet.dumont.connector.config.DumTuringCsrfService;

/**
 * Turns a source's coverage history + drift (roadmap <strong>D15</strong>) into an LLM health summary,
 * reusing Turing's generic {@code POST /api/v2/summary} surface (the same path as the connector
 * insights). The frontend renders it through the shared {@code AiSummaryPanel}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
@Service
public class DumCoverageHealthService {

    private static final String SYSTEM_PROMPT = """
            You are a data-quality analyst for the Viglet Dumont data-exchange platform.

            Dumont indexes structured sources (catalogs, listings) into Viglet Turing ES. Each full \
            index run records, per declared field, the percentage of published documents that carried \
            a value for that field ("coverage"). A field whose coverage drops sharply between two runs \
            is almost always a layout or selector break upstream — the field stopped being extracted — \
            which silently degrades search quality until someone notices.

            You are given one source's recent coverage runs (newest first) and the drift between its two \
            most recent runs. Produce a concise Markdown health assessment with these sections:
            ## Health
            One-line verdict: healthy, watch, or degraded — and why.
            ## Drift
            Name any field whose coverage dropped sharply and the likely cause (selector/layout break, \
            upstream content removal, or a smaller run). If no drift, say so plainly.
            ## Coverage
            Note fields that are consistently low or trending down, and any mandatory field at risk.
            ## Suggestions
            Actionable next steps (re-check selectors, re-run, compare against the source page).

            Be specific and terse. Do not invent fields or numbers beyond the data provided.""";

    private final RestClient restClient;
    private final DumTuringCsrfService csrfService;
    private final DumCoverageHistoryStore coverageHistoryStore;

    public DumCoverageHealthService(RestClient restClient, DumTuringCsrfService csrfService,
            DumCoverageHistoryStore coverageHistoryStore) {
        this.restClient = restClient;
        this.csrfService = csrfService;
        this.coverageHistoryStore = coverageHistoryStore;
    }

    /** Build the coverage data blob for {@code source} and proxy it to Turing for an LLM summary. */
    public SummaryResponse summarize(String source, int driftThreshold, boolean regenerate) {
        List<DumCoverageSnapshot> history = coverageHistoryStore.history(source);
        if (history.isEmpty()) {
            return new SummaryResponse(false,
                    "No coverage history yet for '" + source + "' — run a full index first.", null, false);
        }
        try {
            DumCoverageDrift drift = history.size() < 2 ? DumCoverageDrift.none(source, driftThreshold)
                    : DumCoverageMonitor.drift(history.get(1), history.get(0), driftThreshold);

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("cacheKey", "dumont-coverage-" + source);
            request.put("data", collectCoverageData(source, history, drift));
            request.put("systemPrompt", SYSTEM_PROMPT);

            String url = "/api/v2/summary" + (regenerate ? "?regenerate=true" : "");
            var csrf = csrfService.fetch();

            return restClient.post()
                    .uri(url)
                    .header(csrf.headerName(), csrf.token())
                    .body(request)
                    .retrieve()
                    .body(SummaryResponse.class);
        } catch (Exception e) {
            return new SummaryResponse(false,
                    "Failed to connect to Turing for coverage summary: " + e.getMessage(), null, false);
        }
    }

    private String collectCoverageData(String source, List<DumCoverageSnapshot> history,
            DumCoverageDrift drift) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Coverage report for source '").append(source).append("'\n\n");
        sb.append("## Drift (two most recent runs)\n");
        sb.append("- Drift threshold: ").append(drift.dropThreshold()).append(" percentage points\n");
        sb.append("- Drift detected: ").append(drift.hasDrift() ? "YES" : "no").append("\n");
        if (drift.hasDrift()) {
            sb.append("- Fields with a sharp coverage drop: ")
                    .append(String.join(", ", drift.driftedFields())).append("\n");
        }
        drift.fields().forEach(field -> sb.append("  - ").append(field.field()).append(": ")
                .append(field.previousPercent()).append("% → ").append(field.currentPercent())
                .append("% (Δ ").append(field.delta()).append(")\n"));

        sb.append("\n## Coverage history (newest first, up to 10 runs)\n");
        history.stream().limit(10).forEach(run -> {
            sb.append("### Run @ ").append(run.capturedAt()).append(" — ")
                    .append(run.documentCount()).append(" documents\n");
            for (DumFieldCoverage field : run.fields()) {
                sb.append("- ").append(field.field()).append(" (").append(field.type())
                        .append(field.mandatory() ? ", mandatory" : "").append("): ")
                        .append(field.percent()).append("% (").append(field.present()).append("/")
                        .append(field.total()).append(")\n");
            }
        });
        sb.append("\nAnalyze the coverage trend and drift, and assess this source's data-quality health.");
        return sb.toString();
    }
}
