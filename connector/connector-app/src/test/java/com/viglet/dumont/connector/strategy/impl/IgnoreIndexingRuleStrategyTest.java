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

package com.viglet.dumont.connector.strategy.impl;

import static com.viglet.dumont.commons.indexing.DumIndexingStatus.IGNORED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.batch.JobItemBatchProcessor;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingRuleModel;
import com.viglet.dumont.connector.service.DumConnectorIndexingRuleService;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;

@ExtendWith(MockitoExtension.class)
class IgnoreIndexingRuleStrategyTest {

    @Mock
    private DumConnectorIndexingService indexingService;
    @Mock
    private DumConnectorIndexingRuleService indexingRuleService;
    @Mock
    private JobItemBatchProcessor batchProcessor;

    private IgnoreIndexingRuleStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new IgnoreIndexingRuleStrategy(indexingService, indexingRuleService);
    }

    private static DumJobItemWithSession jobItem(TurSNJobAction action, Map<String, Object> attrs) {
        TurSNJobItem item = new TurSNJobItem(action, List.of("Sample"), Locale.US, attrs);
        DumConnectorSession session = new DumConnectorSession("wknd", "turing", Locale.US);
        return new DumJobItemWithSession(item, session, Set.of(), false);
    }

    private static DumConnectorIndexingRuleModel rule(String attribute, String value) {
        return DumConnectorIndexingRuleModel.builder()
                .attribute(attribute).values(List.of(value)).build();
    }

    private static Map<String, Object> attrs(String type) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", "c1");
        if (type != null) {
            attrs.put("type", type);
        }
        return attrs;
    }

    @Test
    void priorityIsHigh() {
        assertThat(strategy.getPriority()).isEqualTo(20);
    }

    @Test
    void doesNotHandleNonCreateActions() {
        assertThat(strategy.canHandle(jobItem(TurSNJobAction.DELETE, attrs("page")))).isFalse();
    }

    @Test
    void handlesCreateWhenIgnoreRuleMatches() {
        when(indexingRuleService.getIndexingRules(any()))
                .thenReturn(Set.of(rule("type", "page")));
        assertThat(strategy.canHandle(jobItem(TurSNJobAction.CREATE, attrs("page")))).isTrue();
    }

    @Test
    void doesNotHandleCreateWhenNoRuleMatches() {
        when(indexingRuleService.getIndexingRules(any()))
                .thenReturn(Set.of(rule("type", "page")));
        assertThat(strategy.canHandle(jobItem(TurSNJobAction.CREATE, attrs("article")))).isFalse();
    }

    @Test
    void processSavesIgnoredAndQueuesDeleteWhenNotYetIndexed() {
        DumJobItemWithSession jobItem = jobItem(TurSNJobAction.CREATE, attrs("page"));
        when(indexingService.exists(jobItem)).thenReturn(false);

        strategy.process(jobItem, batchProcessor);

        verify(indexingService).save(jobItem, IGNORED);
        verify(batchProcessor).add(any(TurSNJobItem.class), any(DumConnectorSession.class));
    }

    @Test
    void processUpdatesExistingIgnoredRecords() {
        DumJobItemWithSession jobItem = jobItem(TurSNJobAction.CREATE, attrs("page"));
        when(indexingService.exists(jobItem)).thenReturn(true);
        when(indexingService.getList(jobItem))
                .thenReturn(List.of(DumConnectorIndexingModel.builder().objectId("c1").build()));

        strategy.process(jobItem, batchProcessor);

        verify(indexingService).update(jobItem);
        verify(batchProcessor).add(any(TurSNJobItem.class), any(DumConnectorSession.class));
    }
}
