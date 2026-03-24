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

package com.viglet.dumont.connector.plugin.aem.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.plugin.aem.DumAemPluginProcess;
import com.viglet.dumont.connector.plugin.aem.api.DumAemPathList;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.navigator.AemNodeNavigator;
import com.viglet.dumont.connector.plugin.aem.service.DumAemObjectService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSessionService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSourceService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Primary
@Component("aem")
public class DumAemPlugin implements DumConnectorPlugin {
    private final DumAemPluginProcess dumAemPluginProcess;
    private final DumAemSourceService dumAemSourceService;
    private final DumAemService dumAemService;
    private final DumAemSessionService dumAemSessionService;
    private final DumAemObjectService dumAemObjectService;
    private final AemNodeNavigator aemNodeNavigator;

    public DumAemPlugin(DumAemPluginProcess dumAemPluginProcess,
            DumAemSourceService dumAemSourceService,
            DumAemService dumAemService,
            DumAemSessionService dumAemSessionService,
            DumAemObjectService dumAemObjectService,
            AemNodeNavigator aemNodeNavigator) {
        this.dumAemPluginProcess = dumAemPluginProcess;
        this.dumAemSourceService = dumAemSourceService;
        this.dumAemService = dumAemService;
        this.dumAemSessionService = dumAemSessionService;
        this.dumAemObjectService = dumAemObjectService;
        this.aemNodeNavigator = aemNodeNavigator;
    }

    @Override
    public void crawl() {
        dumAemSourceService.getAllSources().forEach(dumAemPluginProcess::indexAll);
    }

    @Override
    public void indexAll(String source) {
        dumAemPluginProcess.indexAllByNameAsync(source);
    }

    public void indexById(String source, List<String> contentId) {
        DumAemPathList dumAemPathList = DumAemPathList.builder()
                .paths(contentId)
                .event(DumAemEvent.INDEXING)
                .recursive(false)
                .build();
        dumAemPluginProcess.sentToIndexStandalone(source, dumAemPathList);
    }

    @Override
    public String getProviderName() {
        return dumAemService.getProviderName();
    }

    @Override
    public Locale resolveLocale(String source, String objectId) {
        return dumAemSourceService.getDumAemSourceByName(source)
                .map(dumAemSource -> {
                    DumAemSession session = dumAemSessionService.getDumAemSession(dumAemSource, false);
                    return DumAemCommonsUtils.getLocaleByPath(session.getConfiguration(), objectId);
                })
                .orElse(null);
    }

    @Override
    public List<EnvironmentInfo> resolveEnvironments(String source, String objectId) {
        return dumAemSourceService.getDumAemSourceByName(source)
                .map(dumAemSource -> {
                    DumAemSession session = dumAemSessionService.getDumAemSession(dumAemSource, false);
                    DumAemConfiguration config = session.getConfiguration();
                    List<EnvironmentInfo> environments = new ArrayList<>();

                    return DumAemCommonsUtils.getInfinityJson(objectId, config, false)
                            .map(infinityJson -> {
                                if (config.isAuthor()) {
                                    environments.add(new EnvironmentInfo(
                                            DumAemEnv.AUTHOR.toString(),
                                            List.of(config.getAuthorSNSite())));
                                }
                                if (config.isPublish()) {
                                    DumAemObjectGeneric aemObject = dumAemObjectService
                                            .getDumAemObjectGeneric(objectId, infinityJson, DumAemEvent.INDEXING);
                                    if (aemObject.isDelivered()) {
                                        environments.add(new EnvironmentInfo(
                                                DumAemEnv.PUBLISHING.toString(),
                                                List.of(config.getPublishSNSite())));
                                    }
                                }
                                return environments;
                            })
                            .orElse(Collections.emptyList());
                })
                .orElse(Collections.emptyList());
    }

    @Override
    public List<String> discoverContentIds(String source) {
        return dumAemSourceService.getDumAemSourceByName(source)
                .map(dumAemSource -> {
                    DumAemSession session = dumAemSessionService.getDumAemSession(dumAemSource, false);
                    DumAemConfiguration config = session.getConfiguration();
                    String rootPath = config.getRootPath();

                    return DumAemCommonsUtils.getInfinityJson(rootPath, config, false)
                            .map(infinityJson -> aemNodeNavigator.collectAccessiblePaths(
                                    session, rootPath, infinityJson))
                            .orElseGet(() -> {
                                log.warn("Root path '{}' not accessible for source '{}'", rootPath, source);
                                return Collections.<String>emptyList();
                            });
                })
                .orElseGet(() -> {
                    log.error("Source '{}' not found", source);
                    return Collections.emptyList();
                });
    }
}
