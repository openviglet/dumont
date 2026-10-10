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

package com.viglet.dumont.connector.plugin.jobs.source;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;
import com.viglet.dumont.connector.plugin.jobs.DumJobsPlugin;
import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSeenRepository;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSourceRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves job sources from the JPA store for the structured indexing loop (trabalharei
 * <strong>TRA13</strong>). Only enabled sources are listed, and each runs on the
 * {@link DumJobSourceAdapter} whose {@link DumJobSourceAdapter#id() id} matches its
 * {@code sourceType}. A source whose type has no adapter (unknown, or not implemented yet) is skipped
 * with a log line — never a failed run.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class DumJobsSourceProvider implements DumStructuredSourceProvider {

    private final DumJobSourceRepository sourceRepository;
    private final DumJobSeenRepository seenRepository;
    private final Map<String, DumJobSourceAdapter> adapters;
    private final Clock clock;

    @Autowired
    public DumJobsSourceProvider(DumJobSourceRepository sourceRepository,
            DumJobSeenRepository seenRepository, ObjectProvider<DumJobSourceAdapter> adapters) {
        this(sourceRepository, seenRepository, adapters.orderedStream().toList(), Clock.systemUTC());
    }

    public DumJobsSourceProvider(DumJobSourceRepository sourceRepository,
            DumJobSeenRepository seenRepository, List<DumJobSourceAdapter> adapters, Clock clock) {
        this.sourceRepository = sourceRepository;
        this.seenRepository = seenRepository;
        this.clock = clock;
        this.adapters = new LinkedHashMap<>();
        for (DumJobSourceAdapter adapter : adapters) {
            String key = typeKey(adapter.id());
            if (this.adapters.putIfAbsent(key, adapter) != null) {
                log.warn("Two job adapters claim source type '{}' — keeping the first", key);
            }
        }
    }

    @Override
    public String getProviderName() {
        return DumJobsPlugin.PROVIDER_NAME;
    }

    @Override
    public Collection<String> sourceNames() {
        return sourceRepository.findByEnabledTrue().stream()
                .filter(source -> adapterFor(source).isPresent())
                .map(DumJobSource::getName)
                .toList();
    }

    @Override
    public Optional<DumStructuredSource> resolve(String sourceName) {
        return sourceRepository.findByName(sourceName)
                .filter(DumJobSource::isEnabled)
                .flatMap(this::resolve);
    }

    /** The running source for {@code source}, or empty when no adapter serves its type. */
    public Optional<DumStructuredSource> resolve(DumJobSource source) {
        return adapterFor(source).map(adapter -> new DumJobsStructuredSource(source,
                DumJobsSchema.schema(), adapter, seenRepository, clock));
    }

    private Optional<DumJobSourceAdapter> adapterFor(DumJobSource source) {
        DumJobSourceAdapter adapter = adapters.get(typeKey(source.getSourceType()));
        if (adapter == null) {
            log.warn("Job source '{}' has type '{}', which no adapter serves — skipped",
                    source.getName(), source.getSourceType());
        }
        return Optional.ofNullable(adapter);
    }

    private static String typeKey(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }
}
