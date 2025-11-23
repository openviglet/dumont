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

package com.viglet.dumont.connector.plugin.aem;

import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.JCR;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.REP;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.STATIC_FILE_SUB_TYPE;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.aem.commons.DumAemCommonsUtils;
import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.plugin.aem.api.DumAemPathList;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.service.DumAemJobService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemObjectService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemReactiveUtils;
import com.viglet.dumont.connector.plugin.aem.service.DumAemService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSessionService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSourceService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Alexandre Oliveira
 * @since 2025.2
 */
@Slf4j
@Component
public class DumAemPluginProcess {
        private final DumConnectorContext dumConnectorContext;
        private final boolean connectorDependencies;
        private final boolean reativeIndexing;
        private final List<String> runningSources = new ArrayList<>();
        private final DumAemReactiveUtils dumAemReactiveUtils;
        private final DumAemSourceService dumAemSourceService;
        private final DumAemService dumAemService;
        private final DumAemJobService dumAemJobService;
        private final DumAemSessionService dumAemSessionService;
        private final DumAemObjectService dumAemObjectService;

        public DumAemPluginProcess(DumConnectorContext dumConnectorContext,
                        @Value("${dumont.dependencies.enabled:true}") boolean connectorDependencies,
                        @Value("${dumont.reactive.indexing:false}") boolean reativeIndexing,
                        DumAemReactiveUtils dumAemReactiveUtils,
                        DumAemSourceService dumAemSourceService,
                        DumAemService dumAemService,
                        DumAemJobService dumAemJobService,
                        DumAemSessionService dumAemSessionService,
                        DumAemObjectService dumAemObjectService) {
                this.dumConnectorContext = dumConnectorContext;
                this.connectorDependencies = connectorDependencies;
                this.reativeIndexing = reativeIndexing;
                this.dumAemReactiveUtils = dumAemReactiveUtils;
                this.dumAemSourceService = dumAemSourceService;
                this.dumAemService = dumAemService;
                this.dumAemJobService = dumAemJobService;
                this.dumAemSessionService = dumAemSessionService;
                this.dumAemObjectService = dumAemObjectService;
        }

        @Async
        public void indexAllByNameAsync(String sourceName) {
                dumAemSourceService.getDumAemSourceByName(sourceName).ifPresent(this::indexAll);
        }

        @Async
        public void indexAllByIdAsync(String id) {
                dumAemSourceService.getDumAemSourceById(id).ifPresent(this::indexAll);
        }

        @Async
        public void sentToIndexStandaloneAsync(@NotNull String source,
                        @NotNull DumAemPathList dumAemPathList) {
                sentToIndexStandalone(source, dumAemPathList);
        }

        public void sentToIndexStandalone(@NotNull String source,
                        @NotNull DumAemPathList dumAemPathList) {
                if (CollectionUtils.isEmpty(dumAemPathList.getPaths())) {
                        log.warn("Received empty payload for source: {}", source);
                        return;
                }

                log.info("Processing payload for source '{}' with paths: {}", source,
                                dumAemPathList.getPaths());
                dumAemSourceService.getDumAemSourceByName(source).ifPresentOrElse(dumAemSource -> {
                        DumAemSession dumAemSession = dumAemSessionService.getDumAemSession(dumAemSource,
                                        dumAemPathList);
                        indexContentIdList(dumAemPathList.getPaths(), dumAemSession);
                        if (connectorDependencies) {
                                indexDependencies(dumAemSession, dumAemPathList.getPaths());
                        }
                        finished(dumAemSession);
                }, () -> log.error("Source '{}' not found", source));
        }

        private void indexContentIdList(List<String> contentIdList, DumAemSession dumAemSession) {
                contentIdList.stream().filter(StringUtils::isNotBlank)
                                .forEach(path -> indexContentId(dumAemSession, path));
        }

        public void indexContentId(DumAemSession dumAemSession, String contentId) {
                if (!isValidSession(dumAemSession, contentId)) {
                        return;
                }

                if (StringUtils.isBlank(contentId)) {
                        log.debug("Ignoring blank contentId");
                        return;
                }

                DumAemCommonsUtils
                                .getInfinityJson(contentId, dumAemSession.getConfiguration(), false)
                                .ifPresentOrElse(
                                                infinityJson -> processIndexingContent(
                                                                dumAemSession, contentId,
                                                                infinityJson),
                                                () -> handleMissingContent(dumAemSession,
                                                                contentId));
        }

        private boolean isValidSession(DumAemSession dumAemSession, String contentId) {
                if (dumAemSession == null || dumAemSession.getConfiguration() == null) {
                        log.warn("Session or configuration is null for contentId: {}", contentId);
                        return false;
                }
                return true;
        }

        private void processIndexingContent(DumAemSession dumAemSession, String contentId,
                        JSONObject infinityJson) {
                DumAemObjectGeneric dumAemObjectGeneric = dumAemObjectService.getDumAemObjectGeneric(contentId,
                                infinityJson, dumAemSession.getEvent());
                log.debug("Processing content with primaryType: {} for contentId: {}",
                                dumAemObjectGeneric.getType(),
                                dumAemObjectGeneric.getPath());
                getNodeFromJson(dumAemSession, dumAemObjectGeneric);

        }

        private void handleMissingContent(DumAemSession dumAemSession, String contentId) {
                try {
                        log.debug("Content not found, creating de-index job for contentId: {}",
                                        contentId);
                        dumAemJobService.createDeIndexJobAndSendToConnectorQueue(dumAemSession,
                                        contentId);
                } catch (Exception e) {
                        log.error("Failed to create de-index job for contentId {}: {}", contentId,
                                        e.getMessage(), e);
                }
        }

        public void indexAll(DumAemSource dumAemSource) {
                if (runningSources.contains(dumAemSource.getName())) {
                        log.warn("Skipping. There are already source process running. {}",
                                        dumAemSource.getName());
                        return;
                }
                runningSources.add(dumAemSource.getName());
                DumAemSession dumAemSession = dumAemSessionService.getDumAemSession(dumAemSource);
                try {
                        this.getNodesFromJson(dumAemSession);
                } catch (Exception e) {
                        log.error(e.getMessage(), e);
                }
                finished(dumAemSession);
        }

        private void indexDependencies(DumAemSession dumAemSession, List<String> idList) {
                List<String> contentIdList = dumConnectorContext
                                .getObjectIdByDependency(dumAemSession.getSource(),
                                                dumAemService.getProviderName(), idList);
                indexContentIdList(contentIdList, dumAemSession);
        }

        private void finished(DumAemSession dumAemSession) {
                if (!dumAemSession.isStandalone())
                        runningSources.remove(dumAemSession.getSource());
                dumConnectorContext.finishIndexing(dumAemSession, dumAemSession.isStandalone());
        }

        private void getNodesFromJson(DumAemSession dumAemSession) {
                if (!DumAemCommonsUtils.usingContentTypeParameter(dumAemSession.getConfiguration()))
                        return;
                byContentType(dumAemSession);
        }

        private void byContentType(DumAemSession dumAemSession) {
                DumAemConfiguration configuration = dumAemSession.getConfiguration();
                String rootPath = configuration.getRootPath();

                DumAemCommonsUtils.getInfinityJson(rootPath, configuration, false)
                                .ifPresent(infinityJson -> {
                                        DumAemObjectGeneric dumAemObjectGeneric = dumAemObjectService
                                                        .getDumAemObjectGeneric(
                                                                        rootPath,
                                                                        infinityJson,
                                                                        dumAemSession.getEvent());
                                        getNodeFromJson(dumAemSession, dumAemObjectGeneric);

                                });
        }

        private void getNodeFromJson(DumAemSession dumAemSession,
                        DumAemObjectGeneric dumAemObject) {
                processIndexingForContentType(dumAemSession, dumAemObject);
                if (dumAemSession.isIndexChildren()) {
                        getChildrenFromJson(dumAemSession, dumAemObject);
                }
        }

        /**
         * Reactive version of getNodeFromJson
         */
        private Mono<Void> getNodeFromJsonReactive(DumAemSession dumAemSession,
                        DumAemObjectGeneric dumAemObject) {
                processIndexingForContentType(dumAemSession, dumAemObject);
                if (dumAemSession.isIndexChildren()) {
                        return getChildrenFromJsonReactive(dumAemSession, dumAemObject);
                }
                return Mono.empty();
        }

        private void processIndexingForContentType(DumAemSession dumAemSession,
                        DumAemObjectGeneric dumAemObjectGeneric) {
                if (DumAemCommonsUtils.isTypeEqualContentType(dumAemObjectGeneric,
                                dumAemSession.getConfiguration())) {
                        dumAemJobService.prepareIndexObject(dumAemSession, dumAemObjectGeneric);
                }
        }

        private void getChildrenFromJson(DumAemSession dumAemSession,
                        DumAemObjectGeneric dumAemObject) {
                if (reativeIndexing) {
                        try {
                                getChildrenFromJsonReactive(dumAemSession, dumAemObject).block();
                        } catch (Exception e) {
                                log.warn("Reactive processing failed, falling back to synchronous: {}",
                                                e.getMessage(), e);
                        }
                } else {
                        getChildrenFromJsonSynchronous(dumAemSession, dumAemObject);
                }

        }

        private void getChildrenFromJsonSynchronous(DumAemSession dumAemSession,
                        DumAemObjectGeneric dumAemObject) {
                dumAemObject.getJcrNode().toMap().forEach((nodeName, nodeValue) -> {
                        if (isIndexedNode(dumAemSession.getConfiguration(), nodeName)) {
                                String nodePathChild = "%s/%s".formatted(dumAemObject.getPath(), nodeName);
                                if (isNotOnce(dumAemSession, nodePathChild)) {
                                        getChildNode(dumAemSession, nodePathChild);
                                }
                        }
                });
        }

        private void getChildNode(DumAemSession dumAemSession, String nodePathChild) {
                DumAemCommonsUtils.getInfinityJson(nodePathChild, dumAemSession.getConfiguration(),
                                false).ifPresent(infinityJson -> {
                                        getDumAemObjectChild(dumAemSession, nodePathChild,
                                                        infinityJson);
                                });
        }

        private void getDumAemObjectChild(DumAemSession dumAemSession, String nodePathChild,
                        JSONObject infinityJson) {
                DumAemObjectGeneric dumAemObject = dumAemObjectService
                                .getDumAemObjectGeneric(nodePathChild, infinityJson);
                getNodeFromJson(dumAemSession, dumAemObject);
        }

        private boolean isNotOnce(DumAemSession dumAemSession, String nodePathChild) {
                DumAemConfiguration config = dumAemSession.getConfiguration();
                return !dumAemSourceService.isOnce(config)
                                || DumAemCommonsUtils.isNotOnceConfig(nodePathChild, config);
        }

        private static boolean isIndexedNode(DumAemConfiguration dumAemSourceContext,
                        String nodeName) {
                return !nodeName.startsWith(JCR)
                                && !nodeName.startsWith(REP)
                                && !nodeName.startsWith(CQ)
                                && (dumAemSourceContext.getSubType() != null
                                                && dumAemSourceContext.getSubType()
                                                                .equals(STATIC_FILE_SUB_TYPE)
                                                || DumAemCommonsUtils
                                                                .checkIfFileHasNotImageExtension(
                                                                                nodeName));
        }

        /**
         * Reactive version of getChildrenFromJson that processes children using WebFlux
         */
        private Mono<Void> getChildrenFromJsonReactive(DumAemSession dumAemSession,
                        DumAemObjectGeneric dumAemObject) {
                DumAemConfiguration config = dumAemSession.getConfiguration();
                return Flux.fromIterable(dumAemObject.getJcrNode().toMap().entrySet())
                                .filter(entry -> isIndexedNode(config, entry.getKey()))
                                .flatMap(entry -> {
                                        String nodeName = entry.getKey();
                                        String nodePathChild = "%s/%s".formatted(
                                                        dumAemObject.getPath(), nodeName);
                                        if (!dumAemSourceService.isOnce(config)
                                                        || DumAemCommonsUtils.isNotOnceConfig(
                                                                        nodePathChild, config)) {
                                                return dumAemReactiveUtils
                                                                .getInfinityJsonReactive(
                                                                                nodePathChild,
                                                                                config)
                                                                .flatMap(infinityJson -> getNodeFromJsonReactive(
                                                                                dumAemSession,
                                                                                dumAemObject));
                                        }
                                        return Mono.<Void>empty();
                                }, 10).then();
        }

}
