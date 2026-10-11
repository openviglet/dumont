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

import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnrichmentChain;
import com.viglet.dumont.connector.plugin.jobs.DumJobsPlugin;
import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSourceRepository;
import com.viglet.dumont.connector.plugin.jobs.source.location.DumJobLocationEnricher;
import com.viglet.dumont.connector.plugin.jobs.source.salary.DumJobSalaryEnricher;

/**
 * One run of an adapter through the structured source and the plugin's whole enrichment chain
 * (discover &rarr; extract &rarr; enrich), as the jobs plugin runs it — the records it would publish.
 */
public final class EnrichedRun {

    public final DumJobsStructuredSource source;
    public final DumJobSalaryEnricher salary = new DumJobSalaryEnricher();
    public final Map<String, DumStructuredRecord> records = new LinkedHashMap<>();

    private EnrichedRun(DumJobsStructuredSource source) {
        this.source = source;
    }

    /** Run {@code adapter} for {@code entity} through the chain {@link DumJobsPlugin} installs. */
    public static EnrichedRun of(DumJobSourceAdapter adapter, DumJobSource entity, Clock clock) {
        DumJobsStructuredSource source = new DumJobsStructuredSource(entity, DumJobsSchema.schema(), adapter,
                new InMemorySeenRepository().repository, new InMemoryCallRepository().repository,
                mock(DumJobSourceRepository.class), clock);
        EnrichedRun run = new EnrichedRun(source);
        DumEnrichmentChain chain = DumJobsPlugin.chain(DumJobLocationEnricher.standard(), run.salary);
        for (String id : source.discover()) {
            run.records.put(id, chain.apply(source, source.extract(id).orElseThrow()));
        }
        return run;
    }

    /**
     * Each published title and the occupation published with it ({@code "-"} when none), in record
     * order; repeated titles appear once.
     */
    public Map<String, String> occupationsByTitle() {
        Map<String, String> occupations = new LinkedHashMap<>();
        for (DumStructuredRecord rec : records.values()) {
            Object occupation = first(rec.values().get(DumJobsSchema.OCCUPATION));
            occupations.putIfAbsent(String.valueOf(first(rec.values().get(DumJobsSchema.TITLE))),
                    occupation == null ? "-" : occupation.toString());
        }
        return occupations;
    }

    private static Object first(Object value) {
        if (value instanceof java.util.Collection<?> values) {
            return values.isEmpty() ? null : values.iterator().next();
        }
        return value;
    }

    /** The published values of {@code id}. */
    public Map<String, Object> values(String id) {
        DumStructuredRecord rec = records.get(id);
        if (rec == null) {
            throw new IllegalArgumentException("No record " + id + " in " + records.keySet());
        }
        return rec.values();
    }
}
