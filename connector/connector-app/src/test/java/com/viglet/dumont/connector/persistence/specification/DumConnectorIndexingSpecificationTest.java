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

package com.viglet.dumont.connector.persistence.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import com.viglet.dumont.commons.indexing.DumIndexingStatus;
import com.viglet.dumont.connector.domain.DumConnectorMonitoringRequest;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Covers {@link DumConnectorIndexingSpecification}: both the value branch (every filter set) and the
 * absent branch (empty request, null provider) of each composed predicate, driving the lambdas
 * through {@code toPredicate} with mocked JPA Criteria objects.
 */
class DumConnectorIndexingSpecificationTest {

    @SuppressWarnings("unchecked")
    private final Root<DumConnectorIndexingModel> root = mock(Root.class, RETURNS_DEEP_STUBS);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

    @Test
    void buildsPredicateWhenEveryFilterIsSet() {
        DumConnectorMonitoringRequest request = DumConnectorMonitoringRequest.builder()
                .source("wknd")
                .objectId("/content/wknd")
                .statuses(List.of(DumIndexingStatus.INDEXED))
                .environment("AUTHOR")
                .locale("pt_BR")
                .site("wknd-site")
                .dateFrom(Instant.parse("2024-01-01T00:00:00Z"))
                .dateTo(Instant.parse("2024-12-31T00:00:00Z"))
                .build();

        Specification<DumConnectorIndexingModel> spec =
                DumConnectorIndexingSpecification.fromRequest(request, "turing");
        Predicate predicate = spec.toPredicate(root, query, cb);

        assertThat(predicate).isNotNull();
    }

    @Test
    void appliesNoRestrictionsWhenRequestIsEmptyAndProviderNull() {
        DumConnectorMonitoringRequest request = DumConnectorMonitoringRequest.builder()
                .source("  ").objectId("").environment(" ").locale("").site("  ").build();

        Specification<DumConnectorIndexingModel> spec =
                DumConnectorIndexingSpecification.fromRequest(request, null);

        // Every component resolves to a null predicate, so the composed specification adds no
        // restriction — exercising the absent branch of each filter without throwing.
        assertThat(spec.toPredicate(root, query, cb)).isNull();
    }
}
