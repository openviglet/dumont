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

import java.util.Optional;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.resolve.DumResolutionIndex;
import com.viglet.dumont.connector.commons.spi.structured.resolve.DumResolutionIndexFactory;
import com.viglet.dumont.connector.persistence.repository.DumResolutionMemberRepository;

/**
 * The persistent {@link DumResolutionIndexFactory} (roadmap <strong>D109</strong>): each provider's
 * index lives in {@code dum_resolution_member}, so cross-source resolution survives a restart.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
@Component
public class DumJpaResolutionIndexFactory implements DumResolutionIndexFactory {

    private final DumResolutionMemberRepository repository;
    private final TransactionTemplate transaction;

    @Autowired
    public DumJpaResolutionIndexFactory(DumResolutionMemberRepository repository,
            PlatformTransactionManager transactionManager) {
        this(repository, new TransactionTemplate(transactionManager));
    }

    DumJpaResolutionIndexFactory(DumResolutionMemberRepository repository, TransactionTemplate transaction) {
        this.repository = repository;
        this.transaction = transaction;
    }

    @Override
    public DumResolutionIndex forProvider(String provider,
            Function<String, Optional<DumStructuredSchema>> schemaOfSource) {
        return new DumJpaResolutionIndex(provider, schemaOfSource, repository, transaction);
    }
}
