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

package com.viglet.dumont.connector.plugin.catalog.migration;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.parity.DumParityCriteria;
import com.viglet.dumont.connector.commons.spi.structured.parity.DumParityGate;
import com.viglet.dumont.connector.commons.spi.structured.parity.DumParityReport;
import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogSource;
import com.viglet.dumont.connector.plugin.catalog.persistence.repository.DumCatalogSourceRepository;
import com.viglet.dumont.connector.plugin.catalog.source.DumCatalogSourceProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs the per-source parity gate and, when it passes, flips a catalog source from the sidecar to its
 * native recipe (roadmap <strong>D11</strong>).
 *
 * <p>
 * {@link #parity(String, DumParityCriteria)} compares the source's native candidate (the recipe it is
 * bound to) against the incumbent sidecar baseline (the Python run) on count + URL coverage, writing
 * nothing. {@link #migrateToNative(String, DumParityCriteria)} re-runs that gate and persists
 * {@link DumCatalogMigrationState#NATIVE} <em>only</em> when it passes — the discipline that no leg
 * flips until it indexes natively at parity. {@link #rollbackToSidecar(String)} is the always-allowed
 * reverse, keeping the Python leg available for the hardest sources.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class DumCatalogParityService {

    private static final String SOURCE_NOT_FOUND = "Catalog source id not found: ";

    private final DumCatalogSourceRepository sourceRepository;
    private final DumCatalogSourceProvider sourceProvider;

    public DumCatalogParityService(DumCatalogSourceRepository sourceRepository,
            DumCatalogSourceProvider sourceProvider) {
        this.sourceRepository = sourceRepository;
        this.sourceProvider = sourceProvider;
    }

    /**
     * Gate the source's native recipe against its sidecar baseline. The source must declare a native
     * binding (recipe + listing URL); it need not be flipped yet.
     *
     * @throws IllegalArgumentException if no source has the given id
     * @throws IllegalStateException    if the source has no native binding to gate, or the recipe is
     *                                  not registered
     */
    public DumParityReport parity(String id, DumParityCriteria criteria) {
        DumCatalogSource source = sourceRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException(SOURCE_NOT_FOUND + id));
        return parity(source, criteria);
    }

    /**
     * Run the gate and, when it passes, persist the flip to {@link DumCatalogMigrationState#NATIVE}.
     * Returns the report either way; the source is left untouched when the gate fails.
     */
    public DumParityReport migrateToNative(String id, DumParityCriteria criteria) {
        DumCatalogSource source = sourceRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException(SOURCE_NOT_FOUND + id));
        DumParityReport report = parity(source, criteria);
        if (report.passed()) {
            source.setMigrationState(DumCatalogMigrationState.NATIVE);
            sourceRepository.save(source);
            log.info("Catalog source '{}' flipped to NATIVE — parity passed (count {}={}, "
                    + "URL coverage {})", source.getName(), report.baselineCount(),
                    report.candidateCount(), report.urlCoverage());
        } else {
            log.warn("Catalog source '{}' stays on the sidecar — parity failed: {}",
                    source.getName(), report.reasons());
        }
        return report;
    }

    /** Reverse a source to the sidecar leg. Always allowed (D09 safe fallback). */
    public DumCatalogSource rollbackToSidecar(String id) {
        DumCatalogSource source = sourceRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException(SOURCE_NOT_FOUND + id));
        source.setMigrationState(DumCatalogMigrationState.SIDECAR);
        return sourceRepository.save(source);
    }

    private DumParityReport parity(DumCatalogSource source, DumParityCriteria criteria) {
        DumStructuredSource candidate = sourceProvider.nativeSource(source).orElseThrow(
                () -> new IllegalStateException("Catalog source '" + source.getName()
                        + "' has no registered native binding (recipe + listing URL) to gate"));
        DumStructuredSource baseline = sourceProvider.sidecarSource(source);
        return DumParityGate.compare(baseline, candidate,
                criteria == null ? DumParityCriteria.defaults() : criteria);
    }
}
