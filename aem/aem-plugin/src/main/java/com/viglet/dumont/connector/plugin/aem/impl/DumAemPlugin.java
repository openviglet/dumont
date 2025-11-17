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

import java.util.List;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;
import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.plugin.aem.DumAemPluginProcess;
import com.viglet.dumont.connector.plugin.aem.api.DumAemPathList;
import com.viglet.dumont.connector.plugin.aem.service.DumAemService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSourceService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Primary
@Component("aem")
public class DumAemPlugin implements DumConnectorPlugin {
    private final DumAemPluginProcess dumAemPluginProcess;
    private final DumAemSourceService dumAemSourceService;
    private final DumAemService dumAemService;

    public DumAemPlugin(DumAemPluginProcess dumAemPluginProcess,
            DumAemSourceService dumAemSourceService,
            DumAemService dumAemService) {
        this.dumAemPluginProcess = dumAemPluginProcess;
        this.dumAemSourceService = dumAemSourceService;
        this.dumAemService = dumAemService;
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
                .event(DumAemEvent.NONE)
                .recursive(false)
                .build();
        dumAemPluginProcess.sentToIndexStandalone(source, dumAemPathList);
    }

    @Override
    public String getProviderName() {
        return dumAemService.getProviderName();
    }
}
