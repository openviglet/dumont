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

package com.viglet.dumont.connector.plugin.catalog.source;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.spi.structured.jsonapi.DumJsonApiState;
import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogJsonApiCall;
import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogJsonApiSeen;
import com.viglet.dumont.connector.plugin.catalog.persistence.repository.DumCatalogJsonApiCallRepository;
import com.viglet.dumont.connector.plugin.catalog.persistence.repository.DumCatalogJsonApiSeenRepository;

/**
 * The JSON API state of catalog sources, kept in the connector's database (roadmap
 * <strong>D92</strong>), so a daily allowance and a record's last sighting survive a restart.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class DumJpaCatalogJsonApiStates implements DumCatalogJsonApiStates {

    private final DumCatalogJsonApiCallRepository callRepository;
    private final DumCatalogJsonApiSeenRepository seenRepository;

    public DumJpaCatalogJsonApiStates(DumCatalogJsonApiCallRepository callRepository,
            DumCatalogJsonApiSeenRepository seenRepository) {
        this.callRepository = callRepository;
        this.seenRepository = seenRepository;
    }

    @Override
    public DumJsonApiState forSource(String sourceName) {
        return new State(sourceName);
    }

    private final class State implements DumJsonApiState {
        private final String sourceName;

        private State(String sourceName) {
            this.sourceName = sourceName;
        }

        @Override
        public long calls(Window window, Instant windowStart) {
            return callRepository.findBySourceNameAndWindowAndWindowStart(sourceName, window.name(), windowStart)
                    .map(DumCatalogJsonApiCall::getCalls).orElse(0L);
        }

        @Override
        public synchronized void addCall(Window window, Instant windowStart) {
            DumCatalogJsonApiCall row = callRepository
                    .findBySourceNameAndWindowAndWindowStart(sourceName, window.name(), windowStart)
                    .orElseGet(() -> {
                        DumCatalogJsonApiCall created = new DumCatalogJsonApiCall();
                        created.setSourceName(sourceName);
                        created.setWindow(window.name());
                        created.setWindowStart(windowStart);
                        return created;
                    });
            row.setCalls(row.getCalls() + 1);
            callRepository.save(row);
        }

        @Override
        public synchronized void markSeen(Collection<String> ids, Instant at) {
            Map<String, DumCatalogJsonApiSeen> known = seenRepository.findBySourceName(sourceName).stream()
                    .collect(Collectors.toMap(DumCatalogJsonApiSeen::getObjectId, Function.identity(),
                            (first, duplicate) -> first));
            List<DumCatalogJsonApiSeen> rows = new ArrayList<>(ids.size());
            for (String id : ids) {
                DumCatalogJsonApiSeen row = known.get(id);
                if (row == null) {
                    row = new DumCatalogJsonApiSeen();
                    row.setSourceName(sourceName);
                    row.setObjectId(id);
                }
                row.setLastSeen(at);
                rows.add(row);
            }
            seenRepository.saveAll(rows);
        }

        @Override
        public synchronized List<String> expire(Instant cutoff) {
            List<DumCatalogJsonApiSeen> expired =
                    seenRepository.findBySourceNameAndLastSeenBefore(sourceName, cutoff);
            seenRepository.deleteAll(expired);
            return expired.stream().map(DumCatalogJsonApiSeen::getObjectId).toList();
        }
    }
}
