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

package com.viglet.dumont.connector.impl;

import static com.viglet.dumont.commons.indexing.DumIndexingStatus.DEINDEXED;
import static com.viglet.dumont.commons.indexing.DumIndexingStatus.IGNORED;
import static com.viglet.dumont.commons.indexing.DumIndexingStatus.PREPARE_FORCED_REINDEX;
import static com.viglet.dumont.commons.indexing.DumIndexingStatus.PREPARE_INDEX;
import static com.viglet.dumont.commons.indexing.DumIndexingStatus.PREPARE_REINDEX;
import static com.viglet.dumont.commons.indexing.DumIndexingStatus.PREPARE_UNCHANGED;
import static com.viglet.dumont.commons.indexing.DumIndexingStatus.SENT_TO_QUEUE;
import static com.viglet.dumont.commons.sn.field.DumSNFieldName.ID;
import static com.viglet.dumont.connector.commons.logging.DumConnectorLoggingUtils.setSuccessStatus;
import static com.viglet.dumont.connector.constant.DumConnectorConstants.CONNECTOR_INDEXING_QUEUE;
import static com.viglet.turing.client.sn.TurSNConstants.ID_ATTR;
import static com.viglet.turing.client.sn.TurSNConstants.SOURCE_APPS_ATTR;
import static com.viglet.turing.client.sn.job.TurSNJobAction.CREATE;
import static com.viglet.turing.client.sn.job.TurSNJobAction.DELETE;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.stereotype.Component;

import com.google.common.collect.Iterators;
import com.viglet.dumont.commons.indexing.DumIndexingStatus;
import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingRuleModel;
import com.viglet.dumont.connector.service.DumConnectorIndexingRuleService;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DumConnectorContextImpl implements DumConnectorContext {

    private final DumConnectorIndexingService indexingService;
    private final DumConnectorIndexingRuleService indexingRuleService;
    private final TurSNJobItems turSNJobItems = new TurSNJobItems();
    private final Queue<DumJobItemWithSession> queueLinks = new LinkedList<>();
    private final JmsMessagingTemplate jmsMessagingTemplate;
    private final int jobSize;

    public DumConnectorContextImpl(@Value("${dumont.job.size:50}") int jobSize,
            DumConnectorIndexingService dumConnectorIndexingService,
            DumConnectorIndexingRuleService indexingRuleService,
            JmsMessagingTemplate jmsMessagingTemplate) {
        this.indexingService = dumConnectorIndexingService;
        this.indexingRuleService = indexingRuleService;
        this.jmsMessagingTemplate = jmsMessagingTemplate;
        this.jobSize = jobSize;

    }

    @Override
    public List<String> getObjectIdByDependency(String source, String provider,
            List<String> dependenciesObjectIdList) {
        if (dependenciesObjectIdList == null || dependenciesObjectIdList.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> dependencies = indexingService.findByDependencies(source, provider, dependenciesObjectIdList)
                .stream().distinct().toList();
        if (!dependencies.isEmpty()) {
            log.info("Found dependencies for {} - {} - {}", source, provider,
                    dependenciesObjectIdList);
            dependencies.forEach(dependency -> log.info("Dependent object: {}", dependency));
        }
        return indexingService.findByDependencies(source, provider, dependenciesObjectIdList)
                .stream().distinct().toList();
    }

    @Override
    public void addJobItem(DumJobItemWithSession dumJobItemWithSession) {
        if (dumJobItemWithSession.turSNJobItem() != null) {
            log.info("Adding {} object to payload.", dumJobItemWithSession.turSNJobItem().getId());
            queueLinks.offer(dumJobItemWithSession);
            processRemainingJobs();
        }
    }

    @Override
    public void finishIndexing(DumConnectorSession session, boolean standalone) {
        if (turSNJobItems.size() > 0) {
            log.info("Sending job to connector queue.");
            sendToMessageQueue(session);
            getInfoQueue();
        } else {
            log.info("No job to send to connector queue.");
        }
        if (!standalone) {
            deIndexObjects(session);
        }
        queueLinks.clear();
    }

    @Override
    public List<DumConnectorIndexing> getIndexingItem(String objectId, String source,
            String provider) {
        return indexingService.getIndexingItem(objectId, source, provider);
    }

    private void processRemainingJobs() {
        while (!queueLinks.isEmpty()) {
            DumJobItemWithSession turSNJobItemWithSession = queueLinks.poll();
            if (turSNJobItemWithSession.standalone()) {
                if (objectNeedBeIndexed(turSNJobItemWithSession)) {
                    indexProcess(turSNJobItemWithSession);
                } else {
                    reIndexProcess(turSNJobItemWithSession);
                }
                continue;
            }
            if (isJobItemToDeIndex(turSNJobItemWithSession)) {
                deIndexProcess(turSNJobItemWithSession);
                continue;
            }
            if (indexingRuleIgnore(turSNJobItemWithSession)) {
                indexingRuleIgnoreProcess(turSNJobItemWithSession);
                continue;
            }
            if (objectNeedBeIndexed(turSNJobItemWithSession)) {
                indexProcess(turSNJobItemWithSession);
            } else {
                if (objectNeedBeReIndexed(turSNJobItemWithSession)) {
                    reIndexProcess(turSNJobItemWithSession);
                } else {
                    unchangeProcess(turSNJobItemWithSession);
                }
            }
        }
    }

    private void indexingRuleIgnoreProcess(DumJobItemWithSession turSNJobItemWithSession) {
        ignoreIndexingRulesStatus(turSNJobItemWithSession);
        createJobDeleteFromCreate(turSNJobItemWithSession).ifPresent(deIndexJobItem -> {
            DumJobItemWithSession turSNJobItemWithSessionDeIndex = new DumJobItemWithSession(deIndexJobItem,
                    turSNJobItemWithSession.session(),
                    Collections.emptySet(), turSNJobItemWithSession.standalone());
            addJobToMessageQueue(turSNJobItemWithSessionDeIndex);
            setSuccessStatus(turSNJobItemWithSession.turSNJobItem(), DEINDEXED);
        });
    }

    private void unchangeProcess(DumJobItemWithSession turSNJobItemWithSession) {
        unchangedLog(turSNJobItemWithSession);
        modifyIndexing(turSNJobItemWithSession, PREPARE_UNCHANGED);
        setSuccessStatus(turSNJobItemWithSession.turSNJobItem(), PREPARE_UNCHANGED);
    }

    private void indexProcess(DumJobItemWithSession turSNJobItemWithSession) {
        createIndexing(turSNJobItemWithSession);
        addJobToMessageQueue(turSNJobItemWithSession);
    }

    private void deIndexProcess(DumJobItemWithSession turSNJobItemWithSession) {
        indexingService.delete(turSNJobItemWithSession);
        addJobToMessageQueue(turSNJobItemWithSession);
        setSuccessStatus(turSNJobItemWithSession, DEINDEXED);
    }

    private void reIndexProcess(DumJobItemWithSession turSNJobItemWithSession) {
        reindexLog(turSNJobItemWithSession);
        addJobToMessageQueue(turSNJobItemWithSession);
        modifyIndexing(turSNJobItemWithSession, PREPARE_REINDEX);
        setSuccessStatus(turSNJobItemWithSession, PREPARE_REINDEX);
    }

    private Optional<TurSNJobItem> createJobDeleteFromCreate(
            DumJobItemWithSession turSNJobItemWithSession) {
        TurSNJobItem jobItemCreate = turSNJobItemWithSession.turSNJobItem();
        if (!jobItemCreate.getTurSNJobAction().equals(CREATE))
            return Optional.empty();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ID_ATTR, jobItemCreate.getId());
        attributes.put(SOURCE_APPS_ATTR, turSNJobItemWithSession.session().getProviderName());
        return Optional.of(new TurSNJobItem(DELETE, jobItemCreate.getSiteNames(),
                jobItemCreate.getLocale(), attributes));
    }

    private boolean isJobItemToDeIndex(DumJobItemWithSession turSNJobItemWithSession) {
        return turSNJobItemWithSession.turSNJobItem().getTurSNJobAction().equals(DELETE);
    }

    private void ignoreIndexingRulesStatus(DumJobItemWithSession turSNJobItemWithSession) {
        if (indexingService.exists(turSNJobItemWithSession)) {
            ignoreIndexingRulesLog(turSNJobItemWithSession);
            indexingService.getList(turSNJobItemWithSession)
                    .forEach(indexing -> indexingService.update(turSNJobItemWithSession, indexing));
        } else {
            ignoreIndexingRulesLog(turSNJobItemWithSession);
            indexingService.save(turSNJobItemWithSession, IGNORED);
        }
        setSuccessStatus(turSNJobItemWithSession, IGNORED);
    }

    private void ignoreIndexingRulesLog(DumJobItemWithSession turSNJobItemWithSession) {
        log.info("{} was ignored by Indexing Rules.",
                getObjectDetailForLogs(turSNJobItemWithSession));
    }

    private boolean indexingRuleIgnore(DumJobItemWithSession turSNJobItemWithSession) {
        TurSNJobItem turSNJobItem = turSNJobItemWithSession.turSNJobItem();
        return turSNJobItem.getTurSNJobAction().equals(CREATE)
                && indexingRuleService.getIndexingRules(turSNJobItemWithSession.session()).stream()
                        .anyMatch(rule -> ignoredJobItem(turSNJobItem, rule));
    }

    private boolean ignoredJobItem(TurSNJobItem turSNJobItem, DumConnectorIndexingRuleModel rule) {
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

    private void addJobToMessageQueue(DumJobItemWithSession turSNJobItemWithSession) {
        synchronized (turSNJobItems) {
            turSNJobItems.add(turSNJobItemWithSession.turSNJobItem());
        }
        sendToMessageQueueWhenMaxSize(turSNJobItemWithSession.session());
        getInfoQueue();
    }

    private void sendToMessageQueue(DumConnectorSession session) {
        synchronized (turSNJobItems) {
            if (turSNJobItems.getTuringDocuments().isEmpty()) {
                return;
            }
            if (log.isDebugEnabled()) {
                for (TurSNJobItem turSNJobItem : turSNJobItems) {
                    log.debug("TurSNJobItem Id: {}", turSNJobItem.getAttributes().get(ID));
                }
            }
            for (TurSNJobItem turSNJobItem : turSNJobItems) {
                setSuccessStatus(turSNJobItem, session, SENT_TO_QUEUE);
            }
            this.jmsMessagingTemplate.convertAndSend(CONNECTOR_INDEXING_QUEUE, turSNJobItems);
            turSNJobItems.clear();
        }

    }

    private void getInfoQueue() {
        log.debug("Total Job Item: {}", Iterators.size(turSNJobItems.iterator()));
        log.debug("Queue Size: {}", (long) queueLinks.size());
    }

    private void sendToMessageQueueWhenMaxSize(DumConnectorSession session) {
        if (turSNJobItems.size() >= jobSize) {
            sendToMessageQueue(session);
        }
    }

    private void unchangedLog(DumJobItemWithSession turSNJobItemWithSession) {
        if (!indexingService.exists(turSNJobItemWithSession))
            return;
        log.info("Unchanged {}", getObjectDetailForLogs(turSNJobItemWithSession));
    }

    private void reindexLog(DumJobItemWithSession turSNJobItemWithSession) {
        indexingService.getList(turSNJobItemWithSession)
                .forEach(indexing -> log.info("ReIndexed {} from {} to {}",
                        getObjectDetailForLogs(turSNJobItemWithSession), indexing.getChecksum(),
                        turSNJobItemWithSession.turSNJobItem().getChecksum()));
    }

    private void deIndexObjects(DumConnectorSession session) {
        List<DumConnectorIndexingModel> deindexedItems = indexingService.getShouldBeDeIndexedList(session);
        if (deindexedItems.isEmpty())
            return;
        deindexedItems.forEach(deIndexedItem -> createJobDeleteFromCreate(session, deIndexedItem));
        indexingService.deleteContentsToBeDeIndexed(session);
        sendToMessageQueue(session);
    }

    private void createJobDeleteFromCreate(DumConnectorSession session,
            DumConnectorIndexingModel dumConnectorIndexing) {
        log.info("DeIndex {} object ({} - {} - {}: {})", dumConnectorIndexing.getObjectId(),
                dumConnectorIndexing.getSource(), dumConnectorIndexing.getEnvironment(),
                dumConnectorIndexing.getLocale(), session.getTransactionId());
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ID_ATTR, dumConnectorIndexing.getObjectId());
        attributes.put(SOURCE_APPS_ATTR, session.getProviderName());
        addJobToMessageQueue(new DumJobItemWithSession(
                new TurSNJobItem(DELETE, dumConnectorIndexing.getSites(),
                        dumConnectorIndexing.getLocale(), attributes),
                session, Collections.emptySet(), false));
    }

    private void modifyIndexing(DumJobItemWithSession turSNJobItemWithSession,
            DumIndexingStatus status) {
        List<DumConnectorIndexingModel> indexingModelList = indexingService.getList(turSNJobItemWithSession);
        if (indexingModelList.size() > 1) {
            recreateDuplicatedIndexing(turSNJobItemWithSession);
        } else {
            updateIndexing(turSNJobItemWithSession, indexingModelList, status);
        }
    }

    private void recreateDuplicatedIndexing(DumJobItemWithSession turSNJobItemWithSession) {
        indexingService.delete(turSNJobItemWithSession);
        log.info("Removed duplicated status {}", getObjectDetailForLogs(turSNJobItemWithSession));
        indexingService.save(turSNJobItemWithSession, PREPARE_FORCED_REINDEX);
        setSuccessStatus(turSNJobItemWithSession, PREPARE_FORCED_REINDEX);
        log.info("Recreated status {}", getObjectDetailForLogs(turSNJobItemWithSession));
    }

    private String getObjectDetailForLogs(DumJobItemWithSession turSNJobItemWithSession) {
        TurSNJobItem turSNJobItem = turSNJobItemWithSession.turSNJobItem();
        DumConnectorSession session = turSNJobItemWithSession.session();
        return "%s object (%s - %s - %s: %s)".formatted(turSNJobItem.getId(), session.getSource(),
                turSNJobItem.getEnvironment(), turSNJobItem.getLocale(),
                session.getTransactionId());
    }

    private void updateIndexing(DumJobItemWithSession turSNJobItemWithSession,
            List<DumConnectorIndexingModel> dumConnectorIndexingList, DumIndexingStatus status) {
        indexingService.update(turSNJobItemWithSession, dumConnectorIndexingList, status);
        log.info("Updated status {}", getObjectDetailForLogs(turSNJobItemWithSession));
    }

    private void createIndexing(DumJobItemWithSession turSNJobItemWithSession) {
        indexingService.save(turSNJobItemWithSession, PREPARE_INDEX);
        log.info("Created status {}", getObjectDetailForLogs(turSNJobItemWithSession));
        setSuccessStatus(turSNJobItemWithSession, PREPARE_INDEX);
    }

    private boolean objectNeedBeIndexed(DumJobItemWithSession turSNJobItemWithSession) {
        return (StringUtils.isNotEmpty(turSNJobItemWithSession.turSNJobItem().getId())
                && !indexingService.exists(turSNJobItemWithSession));
    }

    private boolean objectNeedBeReIndexed(DumJobItemWithSession turSNJobItemWithSession) {
        return indexingService.isChecksumDifferent(turSNJobItemWithSession)
                || hasIgnoredStatus(turSNJobItemWithSession);
    }

    private boolean hasIgnoredStatus(DumJobItemWithSession turSNJobItemWithSession) {
        return indexingService.getList(turSNJobItemWithSession).stream()
                .anyMatch(indexing -> IGNORED.equals(indexing.getStatus()));
    }
}
