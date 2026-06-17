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

package com.viglet.dumont.connector.commons.spi.structured.coverage;

import java.util.LinkedHashMap;
import java.util.Map;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumPreviewSnapshot;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenanceHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenanceSnapshot;

/**
 * Buckets a full index run's published records by their {@link DumStructuredRecord#contentType()
 * content type} and, on {@link #flush flush}, records per-type field coverage (roadmap
 * <strong>D15</strong>) and provenance (D16) — so a typed source (roadmap <strong>D25</strong>)
 * reports coverage/drift <em>per content type</em>, not only as one blended source figure.
 *
 * <p>
 * It mirrors the source-level capture {@code DumStructuredSourcePlugin} already does: each record is
 * folded into a {@link DumPreviewSnapshot.Builder} and a {@link DumProvenanceSnapshot.Builder} keyed
 * by its own {@link DumStructuredRecord#schema() type schema}, so coverage is a by-product of the run
 * — no second extraction pass. Records with a {@code null} content type are ignored (an untyped
 * source keeps only its blended source-level history). Per type it appends one run to the history
 * under the {@link #key(String, String) composite key} {@code source::type}, additively alongside the
 * source-level series.
 *
 * <p>
 * Not thread-safe: a single index run feeds one recorder from its publish loop, then flushes once.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class DumContentTypeCoverageRecorder {

    /** Separator between a source name and a content type in a per-type history key. */
    public static final String SEPARATOR = "::";

    private final Map<String, DumStructuredSchema> schemas = new LinkedHashMap<>();
    private final Map<String, DumPreviewSnapshot.Builder> snapshots = new LinkedHashMap<>();
    private final Map<String, DumProvenanceSnapshot.Builder> provenances = new LinkedHashMap<>();

    /**
     * Fold one published record into its content type's bucket. A record with no content type tag is
     * a no-op (the source-level capture still covers it).
     */
    public void add(DumStructuredRecord record) {
        String type = record.contentType();
        if (type == null || type.isBlank()) {
            return;
        }
        DumStructuredSchema schema = record.schema();
        schemas.putIfAbsent(type, schema);
        snapshots.computeIfAbsent(type, t -> DumPreviewSnapshot.builder(schema))
                .add(record.id(), record.values());
        provenances.computeIfAbsent(type, t -> DumProvenanceSnapshot.builder(schema))
                .add(record.values(), record.provenance());
    }

    /** Whether any typed record was bucketed (an untyped run flushes nothing). */
    public boolean isEmpty() {
        return snapshots.isEmpty();
    }

    /**
     * Append each content type's coverage and provenance to its per-type history under the
     * {@link #key(String, String) composite key}. A no-op when no typed record was added.
     */
    public void flush(String source, DumCoverageHistoryStore coverageHistoryStore,
            DumProvenanceHistoryStore provenanceHistoryStore) {
        snapshots.forEach((type, snapshotBuilder) -> {
            String key = key(source, type);
            DumStructuredSchema schema = schemas.get(type);
            DumPreviewSnapshot snapshot = snapshotBuilder.build();
            coverageHistoryStore.record(key, snapshot.documents().size(),
                    DumCoverageMonitor.coverage(schema, snapshot));
            DumProvenanceSnapshot.Builder provenance = provenances.get(type);
            provenanceHistoryStore.record(key, provenance.documentCount(), provenance.fields());
        });
    }

    /** The history-store key a content type's coverage/provenance is recorded under. */
    public static String key(String source, String contentType) {
        return source + SEPARATOR + contentType;
    }
}
