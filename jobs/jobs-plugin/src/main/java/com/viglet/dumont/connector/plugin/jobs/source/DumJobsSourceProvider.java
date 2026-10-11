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
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobCallRepository;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSeenRepository;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSourceRepository;
import com.viglet.dumont.connector.plugin.jobs.source.dedupe.DumJobDeduplicator;
import com.viglet.dumont.connector.plugin.jobs.source.expiry.DumJobLinkChecker;
import com.viglet.dumont.connector.plugin.jobs.source.expiry.DumJobTombstones;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves job sources from the JPA store for the structured indexing loop (trabalharei
 * <strong>TRA13</strong>). Only enabled sources are listed, and each runs on the
 * {@link DumJobSourceAdapter} whose {@link DumJobSourceAdapter#id() id} matches its
 * {@code sourceType}. A source whose type has no adapter (unknown, or not implemented yet) is skipped
 * with a log line — never a failed run.
 *
 * <p>
 * The Spring-built provider hands every source the {@link DumJobDeduplicator} (trabalharei
 * <strong>TRA23</strong>), so the same opening reached through several sources is published once, and
 * the {@link DumJobLinkChecker} and {@link DumJobTombstones} (trabalharei <strong>TRA24</strong>), so
 * dead apply links expire their postings and the job site hears of every document that leaves.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class DumJobsSourceProvider implements DumStructuredSourceProvider {

    private final DumJobSourceRepository sourceRepository;
    private final DumJobSeenRepository seenRepository;
    private final DumJobCallRepository callRepository;
    private final Map<String, DumJobSourceAdapter> adapters;
    private final Clock clock;
    private final DumJobDeduplicator deduplicator;
    private final DumJobLinkChecker linkChecker;
    private final DumJobTombstones tombstones;

    @Autowired
    public DumJobsSourceProvider(DumJobSourceRepository sourceRepository,
            DumJobSeenRepository seenRepository, DumJobCallRepository callRepository,
            ObjectProvider<DumJobSourceAdapter> adapters, DumJobDeduplicator deduplicator,
            DumJobLinkChecker linkChecker, DumJobTombstones tombstones) {
        this(sourceRepository, seenRepository, callRepository, adapters.orderedStream().toList(),
                Clock.systemUTC(), deduplicator, linkChecker, tombstones);
    }

    /** A provider whose sources publish every posting on its own id (no deduplication). */
    public DumJobsSourceProvider(DumJobSourceRepository sourceRepository,
            DumJobSeenRepository seenRepository, DumJobCallRepository callRepository,
            List<DumJobSourceAdapter> adapters, Clock clock) {
        this(sourceRepository, seenRepository, callRepository, adapters, clock, null);
    }

    public DumJobsSourceProvider(DumJobSourceRepository sourceRepository,
            DumJobSeenRepository seenRepository, DumJobCallRepository callRepository,
            List<DumJobSourceAdapter> adapters, Clock clock, DumJobDeduplicator deduplicator) {
        this(sourceRepository, seenRepository, callRepository, adapters, clock, deduplicator, null, null);
    }

    /** A provider whose sources may also check apply links and queue tombstones (either may be null). */
    public DumJobsSourceProvider(DumJobSourceRepository sourceRepository,
            DumJobSeenRepository seenRepository, DumJobCallRepository callRepository,
            List<DumJobSourceAdapter> adapters, Clock clock, DumJobDeduplicator deduplicator,
            DumJobLinkChecker linkChecker, DumJobTombstones tombstones) {
        this.deduplicator = deduplicator;
        this.linkChecker = linkChecker;
        this.tombstones = tombstones;
        this.sourceRepository = sourceRepository;
        this.seenRepository = seenRepository;
        this.callRepository = callRepository;
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
                DumJobsSchema.schema(), adapter, seenRepository, callRepository, sourceRepository,
                clock, deduplicator, linkChecker, tombstones));
    }

    /** Where this provider's sources queue tombstones, or empty when there is nowhere. */
    public Optional<DumJobTombstones> tombstones() {
        return Optional.ofNullable(tombstones);
    }

    /** The deduplicator every source of this provider runs through, or empty when there is none. */
    public Optional<DumJobDeduplicator> deduplicator() {
        return Optional.ofNullable(deduplicator);
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
