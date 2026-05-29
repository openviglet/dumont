/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.plugin.eds.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.dumont.connector.plugin.eds.DumEDSPlugin;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSCrawlState;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSSourceRepository;
import com.viglet.dumont.connector.plugin.eds.state.DumEDSCrawlStateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Read-only healthcheck for the EDS connector. Reports overall status plus
 * per-source roll-ups derived from the crawl-state table:
 *
 * <ul>
 *   <li>{@code pagesIndexed} — number of rows currently bookkept</li>
 *   <li>{@code lastSeenAt}   — most recent crawl that touched the source</li>
 *   <li>{@code lastIndexedAt} — most recent CREATE/UPDATE pushed to Turing</li>
 * </ul>
 *
 * <p>Designed to be cheap (single per-source SELECT) so a Kubernetes liveness
 * probe or Dumont admin dashboard can poll it freely.
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v2/eds/health")
@Tag(name = "EDS Health", description = "Adobe Edge Delivery Services connector health")
public class DumEDSHealthApi {

    public record SourceHealth(
            String id,
            String title,
            String baseUrl,
            int indexCount,
            int pagesIndexed,
            Instant lastSeenAt,
            Instant lastIndexedAt) {
    }

    public record Health(
            String status,
            String providerName,
            int sourceCount,
            List<SourceHealth> sources) {
    }

    public static final String STATUS_UP = "UP";

    private final DumEDSSourceRepository sourceRepository;
    private final DumEDSCrawlStateService crawlStateService;

    public DumEDSHealthApi(DumEDSSourceRepository sourceRepository,
            DumEDSCrawlStateService crawlStateService) {
        this.sourceRepository = sourceRepository;
        this.crawlStateService = crawlStateService;
    }

    @GetMapping
    @Operation(summary = "EDS connector health and per-source status")
    public Health health() {
        List<DumEDSSource> sources = sourceRepository.findAll();
        List<SourceHealth> details = new ArrayList<>();
        for (DumEDSSource source : sources) {
            details.add(summariseSource(source));
        }
        return new Health(STATUS_UP, DumEDSPlugin.PROVIDER_NAME, sources.size(), details);
    }

    private SourceHealth summariseSource(DumEDSSource source) {
        List<DumEDSCrawlState> states = crawlStateService.findAll(source);
        Instant lastSeen = latest(states, DumEDSCrawlState::getLastSeenAt);
        Instant lastIndexed = latest(states, DumEDSCrawlState::getLastIndexedAt);
        int indexCount = source.getIndexes() == null ? 0 : source.getIndexes().size();
        return new SourceHealth(source.getId(), source.getTitle(), source.getBaseUrl(),
                indexCount, states.size(), lastSeen, lastIndexed);
    }

    private static Instant latest(List<DumEDSCrawlState> states,
            java.util.function.Function<DumEDSCrawlState, Instant> field) {
        Optional<Instant> max = states.stream()
                .map(field)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder());
        return max.orElse(null);
    }
}
