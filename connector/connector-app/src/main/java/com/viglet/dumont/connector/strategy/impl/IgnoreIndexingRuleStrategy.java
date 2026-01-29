/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
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

import static com.viglet.dumont.commons.indexing.DumIndexingStatus.DEINDEXED;
import static com.viglet.dumont.commons.indexing.DumIndexingStatus.IGNORED;
import static com.viglet.dumont.connector.commons.logging.DumConnectorLoggingUtils.setSuccessStatus;
import static com.viglet.turing.client.sn.TurSNConstants.ID_ATTR;
import static com.viglet.turing.client.sn.TurSNConstants.SOURCE_APPS_ATTR;
import static com.viglet.turing.client.sn.job.TurSNJobAction.CREATE;
import static com.viglet.turing.client.sn.job.TurSNJobAction.DELETE;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.batch.JobItemBatchProcessor;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingRuleModel;
import com.viglet.dumont.connector.service.DumConnectorIndexingRuleService;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.dumont.connector.strategy.JobProcessingStrategy;
import com.viglet.turing.client.sn.job.TurSNJobItem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Strategy for handling objects that should be ignored by indexing rules.
 * When a rule matches, the object is marked as ignored and removed from index
 * if present.
 * 
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IgnoreIndexingRuleStrategy implements JobProcessingStrategy {

    private final DumConnectorIndexingService indexingService;
    private final DumConnectorIndexingRuleService indexingRuleService;

    @Override
    public void process(DumJobItemWithSession jobItem, JobItemBatchProcessor batchProcessor) {
        // Update or create ignored status
        updateIgnoredStatus(jobItem);

        // If object was previously indexed, create a delete job
        createDeleteJobIfNeeded(jobItem, batchProcessor);
    }

    @Override
    public boolean canHandle(DumJobItemWithSession jobItem) {
        TurSNJobItem turSNJobItem = jobItem.turSNJobItem();

        if (!turSNJobItem.getTurSNJobAction().equals(CREATE)) {
            return false;
        }

        return indexingRuleService.getIndexingRules(jobItem.session()).stream()
                .anyMatch(rule -> matchesIgnoreRule(turSNJobItem, rule));
    }

    @Override
    public int getPriority() {
        return 20; // High priority - check rules before indexing
    }

    private void updateIgnoredStatus(DumJobItemWithSession jobItem) {
        if (indexingService.exists(jobItem)) {
            log.info("{} was ignored by Indexing Rules.", getObjectDetailForLogs(jobItem));
            indexingService.getList(jobItem)
                    .forEach(indexing -> indexingService.update(jobItem));
        } else {
            log.info("{} was ignored by Indexing Rules.", getObjectDetailForLogs(jobItem));
            indexingService.save(jobItem, IGNORED);
        }
        setSuccessStatus(jobItem, IGNORED);
    }

    private void createDeleteJobIfNeeded(DumJobItemWithSession jobItem,
            JobItemBatchProcessor batchProcessor) {
        TurSNJobItem jobItemCreate = jobItem.turSNJobItem();

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ID_ATTR, jobItemCreate.getId());
        attributes.put(SOURCE_APPS_ATTR, jobItem.session().getProviderName());

        TurSNJobItem deIndexJobItem = new TurSNJobItem(
                DELETE,
                jobItemCreate.getSiteNames(),
                jobItemCreate.getLocale(),
                attributes);

        DumJobItemWithSession deleteJobItem = new DumJobItemWithSession(
                deIndexJobItem,
                jobItem.session(),
                Collections.emptySet(),
                jobItem.standalone());

        batchProcessor.add(deleteJobItem.turSNJobItem(), deleteJobItem.session());
        setSuccessStatus(jobItem.turSNJobItem(), DEINDEXED);
    }

    private boolean matchesIgnoreRule(TurSNJobItem turSNJobItem,
            DumConnectorIndexingRuleModel rule) {
        for (String ruleValue : rule.getValues()) {
            if (StringUtils.isNotBlank(ruleValue)) {
                if (!turSNJobItem.containsAttribute(rule.getAttribute())) {
                    return false;
                }
                if (Pattern.compile(ruleValue)
                        .matcher(turSNJobItem.getStringAttribute(rule.getAttribute()))
                        .lookingAt()) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getObjectDetailForLogs(DumJobItemWithSession jobItem) {
        TurSNJobItem turSNJobItem = jobItem.turSNJobItem();
        return "%s object (%s - %s - %s: %s)".formatted(
                turSNJobItem.getId(),
                jobItem.session().getSource(),
                turSNJobItem.getEnvironment(),
                turSNJobItem.getLocale(),
                jobItem.session().getTransactionId());
    }
}
