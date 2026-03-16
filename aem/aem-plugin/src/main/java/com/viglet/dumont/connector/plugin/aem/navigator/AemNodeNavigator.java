/*
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

package com.viglet.dumont.connector.plugin.aem.navigator;

import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.JCR;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.REP;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.STATIC_FILE_SUB_TYPE;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.service.DumAemJobService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemObjectService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSourceService;
import com.viglet.dumont.connector.plugin.aem.utils.DumAemReactiveUtils;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Handles navigation through AEM content tree and triggers indexing.
 * Supports both synchronous and reactive navigation strategies based on
 * configuration.
 * 
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
@Component
public class AemNodeNavigator {

    private static final int DEFAULT_PARALLELISM = 10;

    private final DumAemObjectService objectService;
    private final DumAemJobService jobService;
    private final DumAemSourceService sourceService;
    private final DumAemReactiveUtils reactiveUtils;
    private final boolean reactiveEnabled;
    private final int reactiveParallelism;

    public AemNodeNavigator(
            DumAemObjectService objectService,
            DumAemJobService jobService,
            DumAemSourceService sourceService,
            DumAemReactiveUtils reactiveUtils,
            @Value("${dumont.reactive.indexing:false}") boolean reactiveEnabled,
            @Value("${dumont.reactive.parallelism:10}") int reactiveParallelism) {
        this.objectService = objectService;
        this.jobService = jobService;
        this.sourceService = sourceService;
        this.reactiveUtils = reactiveUtils;
        this.reactiveEnabled = reactiveEnabled;
        this.reactiveParallelism = reactiveParallelism > 0 ? reactiveParallelism : DEFAULT_PARALLELISM;
    }

    /**
     * Navigates to a node and processes it for indexing.
     * 
     * @param session      the AEM session
     * @param path         the node path
     * @param infinityJson the JSON representation of the node
     */
    public void navigateAndIndex(DumAemSession session, String path, JSONObject infinityJson) {
        DumAemObjectGeneric aemObject = objectService.getDumAemObjectGeneric(
                path, infinityJson, session.getEvent());

        log.debug("Navigating node: {} with type: {}", path, aemObject.getType());

        processNode(session, aemObject);

        if (session.isRecursive()) {
            navigateChildren(session, aemObject);
        }
    }

    /**
     * Processes a single node for indexing if it matches the configured content
     * type.
     */
    private void processNode(DumAemSession session, DumAemObjectGeneric aemObject) {
        if (DumAemCommonsUtils.isTypeEqualContentType(aemObject, session.getConfiguration())) {
            jobService.prepareIndexObject(session, aemObject);
        }
    }

    /**
     * Navigates children nodes using the configured strategy (sync or reactive).
     */
    private void navigateChildren(DumAemSession session, DumAemObjectGeneric aemObject) {
        if (reactiveEnabled) {
            try {
                navigateChildrenReactive(session, aemObject).block();
            } catch (Exception e) {
                log.warn("Reactive processing failed, falling back to synchronous: {}", e.getMessage());
                navigateChildrenSync(session, aemObject);
            }
        } else {
            navigateChildrenSync(session, aemObject);
        }
    }

    /**
     * Synchronous children navigation.
     * Iterates through all child nodes and processes them sequentially.
     */
    private void navigateChildrenSync(DumAemSession session, DumAemObjectGeneric aemObject) {
        DumAemConfiguration config = session.getConfiguration();

        aemObject.getJcrNode().toMap().forEach((nodeName, nodeValue) -> {
            if (isIndexableNode(config, nodeName)) {
                String childPath = "%s/%s".formatted(aemObject.getPath(), nodeName);

                if (shouldProcessNode(session, childPath)) {
                    processChildNode(session, childPath);
                }
            }
        });
    }

    /**
     * Reactive children navigation with parallelism.
     * Processes child nodes concurrently using WebFlux.
     */
    private Mono<Void> navigateChildrenReactive(DumAemSession session, DumAemObjectGeneric aemObject) {
        DumAemConfiguration config = session.getConfiguration();

        return Flux.fromIterable(aemObject.getJcrNode().toMap().entrySet())
                .filter(entry -> isIndexableNode(config, entry.getKey()))
                .map(entry -> "%s/%s".formatted(aemObject.getPath(), entry.getKey()))
                .filter(childPath -> shouldProcessNode(session, childPath))
                .flatMap(childPath -> processChildNodeReactive(session, childPath), reactiveParallelism)
                .then();
    }

    /**
     * Processes a child node synchronously.
     */
    private void processChildNode(DumAemSession session, String childPath) {
        DumAemCommonsUtils.getInfinityJson(childPath, session.getConfiguration(), false)
                .ifPresent(infinityJson -> {
                    DumAemObjectGeneric childObject = objectService.getDumAemObjectGeneric(childPath, infinityJson,
                            session.getEvent());
                    processNode(session, childObject);

                    if (session.isRecursive()) {
                        navigateChildrenSync(session, childObject);
                    }
                });
    }

    /**
     * Processes a child node reactively.
     */
    private Mono<Void> processChildNodeReactive(DumAemSession session, String childPath) {
        return reactiveUtils.getInfinityJsonReactive(childPath, session.getConfiguration())
                .flatMap(infinityJson -> {
                    DumAemObjectGeneric childObject = objectService.getDumAemObjectGeneric(childPath, infinityJson,
                            session.getEvent());
                    processNode(session, childObject);

                    if (session.isRecursive()) {
                        return navigateChildrenReactive(session, childObject);
                    }
                    return Mono.empty();
                })
                .onErrorResume(e -> {
                    log.warn("Error processing child path {}: {}", childPath, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Checks if a node should be indexed based on its name.
     * Excludes JCR, REP, and CQ prefixed nodes, and filters based on file
     * extensions.
     * 
     * @param config   the AEM configuration
     * @param nodeName the node name to check
     * @return true if the node should be indexed
     */
    private boolean isIndexableNode(DumAemConfiguration config, String nodeName) {
        if (nodeName.startsWith(JCR) || nodeName.startsWith(REP) || nodeName.startsWith(CQ)) {
            return false;
        }

        boolean isStaticFile = STATIC_FILE_SUB_TYPE.equals(config.getSubType());
        return isStaticFile || DumAemCommonsUtils.checkIfFileHasNotImageExtension(nodeName);
    }

    /**
     * Checks if a node should be processed based on "once" configuration.
     * 
     * @param session  the AEM session
     * @param nodePath the node path
     * @return true if the node should be processed
     */
    private boolean shouldProcessNode(DumAemSession session, String nodePath) {
        DumAemConfiguration config = session.getConfiguration();
        return !sourceService.isOnce(config) || DumAemCommonsUtils.isNotOnceConfig(nodePath, config);
    }
}
