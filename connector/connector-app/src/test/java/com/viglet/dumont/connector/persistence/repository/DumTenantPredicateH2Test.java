/*
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

package com.viglet.dumont.connector.persistence.repository;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * D125: on the embedded H2, with {@code globally_quoted_identifiers}, the {@code tenant_id}
 * column is created quoted as lowercase, so the predicate Hibernate adds for {@code @TenantId}
 * has to name it quoted too, or H2 folds it to {@code TENANT_ID} and every read of a
 * tenant-scoped entity fails. These are the reads a structured run makes at its finish.
 */
@SpringBootTest
class DumTenantPredicateH2Test {

    @Autowired
    private DumConnectorIndexingRepository indexingRepository;
    @Autowired
    private DumConnectorIndexingStatsRepository statsRepository;
    @Autowired
    private DumConnectorIndexingRuleRepository ruleRepository;
    @Autowired
    private DumConnectorDependencyRepository dependencyRepository;

    @Test
    void tenantScopedReadsRunOnEmbeddedH2() {
        assertThatCode(() -> indexingRepository
                .countBySourceAndProviderAndModificationDateGreaterThanEqual("d125", "DB",
                        Instant.EPOCH))
                .doesNotThrowAnyException();
        assertThatCode(() -> indexingRepository.countByProvider("DB")).doesNotThrowAnyException();
        assertThatCode(() -> statsRepository.count()).doesNotThrowAnyException();
        assertThatCode(() -> ruleRepository.findAll()).doesNotThrowAnyException();
        assertThatCode(() -> dependencyRepository.count()).doesNotThrowAnyException();
    }
}
