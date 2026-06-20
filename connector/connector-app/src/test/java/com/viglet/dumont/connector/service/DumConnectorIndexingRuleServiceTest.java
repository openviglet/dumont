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

package com.viglet.dumont.connector.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingRuleModel;
import com.viglet.dumont.connector.persistence.repository.DumConnectorIndexingRuleRepository;

@ExtendWith(MockitoExtension.class)
class DumConnectorIndexingRuleServiceTest {

    @Mock
    private DumConnectorIndexingRuleRepository repository;

    private DumConnectorIndexingRuleService service;

    @BeforeEach
    void setUp() {
        service = new DumConnectorIndexingRuleService(repository);
    }

    @Test
    void getIndexingRulesQueriesIgnoreRulesForSession() {
        DumConnectorSession session = new DumConnectorSession("wknd", "turing", Locale.US);
        when(repository.findBySourceAndRuleType(any(), any())).thenReturn(Set.of());
        assertThat(service.getIndexingRules(session)).isEmpty();
    }

    @Test
    void readDelegations() {
        when(repository.findBySource(any(), any())).thenReturn(Set.of());
        assertThat(service.getBySource("wknd")).isEmpty();

        when(repository.findAll()).thenReturn(List.of());
        assertThat(service.getAll()).isEmpty();

        when(repository.findById("r1")).thenReturn(Optional.empty());
        assertThat(service.getById("r1")).isEmpty();
    }

    @Test
    void updateEditsExistingRule() {
        DumConnectorIndexingRuleModel existing = DumConnectorIndexingRuleModel.builder()
                .id("r1").name("old").build();
        when(repository.findById("r1")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DumConnectorIndexingRuleModel changes = DumConnectorIndexingRuleModel.builder()
                .name("new").attribute("type").source("wknd").values(List.of("page")).build();

        DumConnectorIndexingRuleModel result = service.update("r1", changes);

        assertThat(result.getName()).isEqualTo("new");
        assertThat(result.getAttribute()).isEqualTo("type");
    }

    @Test
    void updateMissingRuleReturnsEmptyModel() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThat(service.update("missing", DumConnectorIndexingRuleModel.builder().build()))
                .isNotNull();
    }

    @Test
    void deleteByIdDelegates() {
        service.deleteById("r1");
        verify(repository).deleteById("r1");
    }

    @Test
    void saveRejectsNullAndPersistsOtherwise() {
        assertThatThrownBy(() -> service.save(null)).isInstanceOf(IllegalArgumentException.class);

        DumConnectorIndexingRuleModel rule =
                DumConnectorIndexingRuleModel.builder().name("r").build();
        when(repository.save(rule)).thenReturn(rule);
        assertThat(service.save(rule)).isSameAs(rule);
    }
}
