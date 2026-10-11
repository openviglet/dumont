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

package com.viglet.dumont.connector.resolve;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.resolve.DumRecordCodec;
import com.viglet.dumont.connector.commons.spi.structured.resolve.DumResolutionIndex;
import com.viglet.dumont.connector.persistence.model.DumResolutionMemberModel;
import com.viglet.dumont.connector.persistence.repository.DumResolutionMemberRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * One provider's {@link DumResolutionIndex} in {@code dum_resolution_member} (roadmap
 * <strong>D109</strong>), so cross-source resolution survives a restart. Records are stored with
 * {@link DumRecordCodec} and read back with their source's current schema; a member whose source the
 * provider no longer has is skipped. Built by {@link DumJpaResolutionIndexFactory}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
@Slf4j
public class DumJpaResolutionIndex implements DumResolutionIndex {

    private final String provider;
    private final Function<String, Optional<DumStructuredSchema>> schemaOfSource;
    private final DumResolutionMemberRepository repository;
    private final TransactionTemplate transaction;

    public DumJpaResolutionIndex(String provider,
            Function<String, Optional<DumStructuredSchema>> schemaOfSource,
            DumResolutionMemberRepository repository, TransactionTemplate transaction) {
        this.provider = provider;
        this.schemaOfSource = schemaOfSource;
        this.repository = repository;
        this.transaction = transaction;
    }

    @Override
    public synchronized void put(String canonicalId, String source, String recordId,
            DumStructuredRecord rec) {
        transaction.executeWithoutResult(status -> {
            DumResolutionMemberModel row = null;
            List<DumResolutionMemberModel> changed = new ArrayList<>();
            for (DumResolutionMemberModel existing : repository.findByProviderAndSourceAndRecordId(provider,
                    source, recordId)) {
                if (existing.getCanonicalId().equals(canonicalId)) {
                    row = existing;
                } else if (existing.isLive()) {
                    existing.setLive(false);
                    changed.add(existing);
                }
            }
            if (row == null) {
                long order = repository.findFirstByProviderOrderByMemberOrderDesc(provider)
                        .map(last -> last.getMemberOrder() + 1).orElse(0L);
                row = DumResolutionMemberModel.builder().provider(provider).canonicalId(canonicalId)
                        .source(source).recordId(recordId).memberOrder(order).build();
            }
            row.setLive(true);
            row.setRecord(DumRecordCodec.encode(rec));
            changed.add(row);
            repository.saveAll(changed);
        });
    }

    @Override
    public List<Member> members(String canonicalId) {
        return repository.findByProviderAndCanonicalIdOrderByMemberOrder(provider, canonicalId).stream()
                .map(this::member).flatMap(Optional::stream).toList();
    }

    @Override
    public List<Member> bySource(String source) {
        return repository.findByProviderAndSource(provider, source).stream()
                .map(this::member).flatMap(Optional::stream).toList();
    }

    @Override
    public Optional<String> canonicalIdOf(String source, String recordId) {
        return repository.findByProviderAndSourceAndRecordId(provider, source, recordId).stream()
                .filter(DumResolutionMemberModel::isLive)
                .map(DumResolutionMemberModel::getCanonicalId).findFirst();
    }

    @Override
    public synchronized void hold(String canonicalId, String source, String recordId) {
        transaction.executeWithoutResult(status -> repository
                .findByProviderAndSourceAndRecordId(provider, source, recordId).stream()
                .filter(row -> row.getCanonicalId().equals(canonicalId) && row.isLive())
                .forEach(row -> {
                    row.setLive(false);
                    repository.save(row);
                }));
    }

    @Override
    public synchronized void drop(String canonicalId) {
        transaction.executeWithoutResult(
                status -> repository.deleteByProviderAndCanonicalId(provider, canonicalId));
    }

    /** The row as a member, or empty when its source is gone or its record no longer reads. */
    private Optional<Member> member(DumResolutionMemberModel row) {
        Optional<DumStructuredSchema> schema = schemaOfSource.apply(row.getSource());
        if (schema.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Member(row.getCanonicalId(), row.getSource(), row.getRecordId(),
                    DumRecordCodec.decode(row.getRecord(), schema.get()), row.isLive(),
                    row.getMemberOrder()));
        } catch (RuntimeException e) {
            log.warn("Resolution member '{}' of source '{}' under '{}' no longer reads, skipped: {}",
                    row.getRecordId(), row.getSource(), row.getCanonicalId(), e.getMessage());
            return Optional.empty();
        }
    }
}
