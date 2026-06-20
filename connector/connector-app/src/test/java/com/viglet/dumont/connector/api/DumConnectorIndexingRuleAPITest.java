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

package com.viglet.dumont.connector.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.api.dto.DumConnectorIndexingRuleRequest;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingRuleModel;
import com.viglet.dumont.connector.service.DumConnectorIndexingRuleService;

@ExtendWith(MockitoExtension.class)
class DumConnectorIndexingRuleAPITest {

    @Mock
    private DumConnectorIndexingRuleService service;

    private DumConnectorIndexingRuleAPI api;

    @BeforeEach
    void setUp() {
        api = new DumConnectorIndexingRuleAPI(service);
    }

    private static DumConnectorIndexingRuleRequest request() {
        DumConnectorIndexingRuleRequest req = new DumConnectorIndexingRuleRequest();
        req.setName("ignore-pages");
        req.setSource("wknd");
        req.setAttribute("type");
        req.setValues(List.of("page"));
        return req;
    }

    @Test
    void listBySourceAndAllDelegate() {
        when(service.getBySource("wknd")).thenReturn(Set.of());
        assertThat(api.dumConnectorIndexingRuleBySourceList("wknd")).isEmpty();

        when(service.getAll()).thenReturn(List.of());
        assertThat(api.dumConnectorIndexingRuleList()).isEmpty();
    }

    @Test
    void getReturnsRuleOrEmptyModel() {
        when(service.getById("r1"))
                .thenReturn(Optional.of(DumConnectorIndexingRuleModel.builder().name("r").build()));
        assertThat(api.dumConnectorIndexingRuleGet("r1").getName()).isEqualTo("r");

        when(service.getById("missing")).thenReturn(Optional.empty());
        assertThat(api.dumConnectorIndexingRuleGet("missing").getName()).isNull();
    }

    @Test
    void updateMapsRequestToModel() {
        when(service.update(eq("r1"), any())).thenAnswer(inv -> inv.getArgument(1));
        DumConnectorIndexingRuleModel result = api.dumConnectorIndexingRuleUpdate("r1", request());
        assertThat(result.getName()).isEqualTo("ignore-pages");
        assertThat(result.getValues()).containsExactly("page");
    }

    @Test
    void addStampsModifiedDateAndSaves() {
        when(service.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DumConnectorIndexingRuleModel result = api.dumConnectorIndexingRuleAdd(request());
        assertThat(result.getName()).isEqualTo("ignore-pages");
        assertThat(result.getLastModifiedDate()).isNotNull();
    }

    @Test
    void deleteDelegatesAndReturnsTrue() {
        assertThat(api.dumConnectorIndexingRuleDelete("r1")).isTrue();
        verify(service).deleteById("r1");
    }

    @Test
    void structureReturnsEmptyModel() {
        assertThat(api.dumConnectorIndexingRuleStructure()).isNotNull();
    }
}
