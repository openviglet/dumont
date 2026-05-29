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

package com.viglet.dumont.connector.plugin.eds.state;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSCrawlState;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSCrawlStateRepository;

/**
 * Owns the (source, path) → state mapping used for incremental crawls and
 * removal detection. Thin wrapper over the JPA repository so the plugin process
 * can stay focused on pipeline orchestration.
 */
@Service
public class DumEDSCrawlStateService {

    private final DumEDSCrawlStateRepository repository;

    public DumEDSCrawlStateService(DumEDSCrawlStateRepository repository) {
        this.repository = repository;
    }

    /** Loads all crawl-state rows for a source, keyed by path (preserves insert order). */
    @Transactional(readOnly = true)
    public Map<String, DumEDSCrawlState> loadByPath(DumEDSSource source) {
        Map<String, DumEDSCrawlState> map = new LinkedHashMap<>();
        for (DumEDSCrawlState state : repository.findByDumEDSSource(source)) {
            map.put(state.getPath(), state);
        }
        return map;
    }

    /**
     * True when the prior state's {@code lastModified} matches the current value
     * and both are non-blank — i.e. we can safely skip the fetch. When either
     * side is blank or the prior state is missing, returns false (we must fetch).
     */
    public boolean isUnchanged(DumEDSCrawlState prior, String currentLastModified) {
        if (prior == null) {
            return false;
        }
        String stored = prior.getLastModified();
        return stored != null && !stored.isBlank()
                && currentLastModified != null && !currentLastModified.isBlank()
                && stored.equals(currentLastModified);
    }

    /**
     * Upserts the state row. Touches {@code lastSeenAt}; {@code lastIndexedAt}
     * is only refreshed when {@code indexed=true} (i.e. we actually enqueued
     * a CREATE/UPDATE for this path).
     */
    @Transactional
    public DumEDSCrawlState upsert(DumEDSSource source, String path, String lastModified,
            String checksum, boolean indexed) {
        Instant now = Instant.now();
        DumEDSCrawlState state = repository.findByDumEDSSourceAndPath(source, path)
                .orElseGet(() -> newState(source, path));
        state.setLastModified(lastModified);
        if (checksum != null && !checksum.isBlank()) {
            state.setChecksum(checksum);
        }
        state.setLastSeenAt(now);
        if (indexed) {
            state.setLastIndexedAt(now);
        }
        return repository.save(state);
    }

    /** Touches {@code lastSeenAt} only — for paths we skipped fetching this crawl. */
    @Transactional
    public DumEDSCrawlState touch(DumEDSCrawlState prior) {
        prior.setLastSeenAt(Instant.now());
        return repository.save(prior);
    }

    @Transactional
    public int delete(DumEDSSource source, String path) {
        return repository.deleteByDumEDSSourceAndPath(source, path);
    }

    @Transactional
    public List<DumEDSCrawlState> findAll(DumEDSSource source) {
        return repository.findByDumEDSSource(source);
    }

    private static DumEDSCrawlState newState(DumEDSSource source, String path) {
        DumEDSCrawlState state = new DumEDSCrawlState();
        state.setDumEDSSource(source);
        state.setPath(path);
        return state;
    }
}
