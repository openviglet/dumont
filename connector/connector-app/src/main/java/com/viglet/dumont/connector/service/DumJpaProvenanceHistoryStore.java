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

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenanceEntry;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenanceHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenanceSnapshot;
import com.viglet.dumont.connector.persistence.model.DumProvenanceFieldModel;
import com.viglet.dumont.connector.persistence.model.DumProvenanceRunModel;
import com.viglet.dumont.connector.persistence.repository.DumProvenanceRunRepository;

/**
 * Persisted {@link DumProvenanceHistoryStore} (roadmap <strong>D16</strong>) — appends each full
 * index run's per-field provenance to {@code dum_provenance_run} and reads it back per source, so the
 * origin of each field and its last-seen run survive a restart (unlike the in-memory default). Mapped
 * through the same tenant discriminator as the rest of the connector's persistence (T398).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
@Service
public class DumJpaProvenanceHistoryStore implements DumProvenanceHistoryStore {

    /** Per-source runs read back for history / last-seen. */
    private static final int MAX_HISTORY = 50;

    private final DumProvenanceRunRepository repository;

    public DumJpaProvenanceHistoryStore(DumProvenanceRunRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public DumProvenanceSnapshot recordSnapshot(String source, int documentCount,
            List<DumFieldProvenanceEntry> fields) {
        Instant capturedAt = Instant.now();
        DumProvenanceRunModel run = DumProvenanceRunModel.builder()
                .source(source)
                .capturedAt(capturedAt)
                .documentCount(documentCount)
                .fields(fields.stream().map(DumJpaProvenanceHistoryStore::toModel).toList())
                .build();
        repository.save(run);
        return toSnapshot(run);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DumProvenanceSnapshot> history(String source) {
        return repository.findAllBySourceOrderByCapturedAtDescIdDesc(source, Limit.of(MAX_HISTORY))
                .stream()
                .map(DumJpaProvenanceHistoryStore::toSnapshot)
                .toList();
    }

    private static DumProvenanceFieldModel toModel(DumFieldProvenanceEntry entry) {
        return DumProvenanceFieldModel.builder()
                .fieldName(entry.field())
                .strategy(entry.strategy())
                .selector(entry.selector())
                .confidence(entry.confidence())
                .present(entry.present())
                .total(entry.total())
                .build();
    }

    private static DumProvenanceSnapshot toSnapshot(DumProvenanceRunModel run) {
        List<DumFieldProvenanceEntry> fields = run.getFields().stream()
                .map(field -> new DumFieldProvenanceEntry(field.getFieldName(), field.getStrategy(),
                        field.getSelector(), field.getConfidence(), field.getPresent(),
                        field.getTotal()))
                .toList();
        long capturedAt = run.getCapturedAt() == null ? 0L : run.getCapturedAt().toEpochMilli();
        return new DumProvenanceSnapshot(run.getSource(), capturedAt, run.getDocumentCount(), fields);
    }
}
