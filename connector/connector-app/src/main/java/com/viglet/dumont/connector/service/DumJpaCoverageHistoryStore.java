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

import java.util.Date;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageSnapshot;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumFieldCoverage;
import com.viglet.dumont.connector.persistence.model.DumCoverageFieldModel;
import com.viglet.dumont.connector.persistence.model.DumCoverageRunModel;
import com.viglet.dumont.connector.persistence.repository.DumCoverageRunRepository;

/**
 * Persisted {@link DumCoverageHistoryStore} (roadmap <strong>D15</strong>) — appends each full index
 * run's per-field coverage to {@code dum_coverage_run} and reads it back per source, so coverage
 * history and drift survive a restart (unlike the in-memory default). Mapped through the same tenant
 * discriminator as the rest of the connector's persistence (T398).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
@Service
public class DumJpaCoverageHistoryStore implements DumCoverageHistoryStore {

    /** Per-source runs read back for history / drift. */
    private static final int MAX_HISTORY = 50;

    private final DumCoverageRunRepository repository;

    public DumJpaCoverageHistoryStore(DumCoverageRunRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public DumCoverageSnapshot record(String source, int documentCount,
            List<DumFieldCoverage> fields) {
        Date capturedAt = new Date();
        DumCoverageRunModel run = DumCoverageRunModel.builder()
                .source(source)
                .capturedAt(capturedAt)
                .documentCount(documentCount)
                .fields(fields.stream().map(DumJpaCoverageHistoryStore::toModel).toList())
                .build();
        repository.save(run);
        return toSnapshot(run);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DumCoverageSnapshot> history(String source) {
        return repository.findAllBySourceOrderByCapturedAtDescIdDesc(source, Limit.of(MAX_HISTORY))
                .stream()
                .map(DumJpaCoverageHistoryStore::toSnapshot)
                .toList();
    }

    private static DumCoverageFieldModel toModel(DumFieldCoverage coverage) {
        return DumCoverageFieldModel.builder()
                .fieldName(coverage.field())
                .fieldType(coverage.type())
                .mandatory(coverage.mandatory())
                .present(coverage.present())
                .total(coverage.total())
                .percent(coverage.percent())
                .build();
    }

    private static DumCoverageSnapshot toSnapshot(DumCoverageRunModel run) {
        List<DumFieldCoverage> fields = run.getFields().stream()
                .map(field -> new DumFieldCoverage(field.getFieldName(), field.getFieldType(),
                        field.isMandatory(), field.getPresent(), field.getTotal(), field.getPercent()))
                .toList();
        long capturedAt = run.getCapturedAt() == null ? 0L : run.getCapturedAt().getTime();
        return new DumCoverageSnapshot(run.getSource(), capturedAt, run.getDocumentCount(), fields);
    }
}
