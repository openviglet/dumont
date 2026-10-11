/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.scheduled;

import java.util.Collection;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingStatsModel.OperationType;
import com.viglet.dumont.connector.plugin.DumConnectorPluginRegistry;
import com.viglet.dumont.connector.service.DumConnectorConfigVarService;
import com.viglet.dumont.connector.service.DumConnectorRunService;
import com.viglet.dumont.connector.service.DumConnectorRunService.RunStart;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DumConnectorScheduledTasks {
    private final DumConnectorPluginRegistry pluginRegistry;
    private final DumConnectorConfigVarService configVarService;
    private final DumConnectorRunService runService;

    public DumConnectorScheduledTasks(DumConnectorPluginRegistry pluginRegistry,
            DumConnectorConfigVarService configVarService, DumConnectorRunService runService) {
        this.pluginRegistry = pluginRegistry;
        this.configVarService = configVarService;
        this.runService = runService;
    }

    @Scheduled(cron = "${dumont.cron:-}", zone = "${dumont.cron.zone:UTC}")
    public void executeWebCrawler() {
        if (configVarService.hasNotFirstTime()) {
            log.info("This is the first time, waiting next schedule.");
        } else {
            for (DumConnectorPlugin plugin : pluginRegistry.connectorPlugins()) {
                crawl(plugin);
            }
        }
    }

    /**
     * A plugin that names its sources has each one started as an on-demand run would be, under
     * its run lock, so a scheduled run and a manual one never overlap and the API sees the
     * scheduled one as running (D128). A plugin that names none crawls on its own.
     */
    private void crawl(DumConnectorPlugin plugin) {
        Collection<String> sources = plugin.scheduledSources();
        if (sources.isEmpty()) {
            plugin.crawl();
            return;
        }
        for (String source : sources) {
            if (runService.start(plugin, source, OperationType.INDEX_ALL) == RunStart.ALREADY_RUNNING) {
                log.info("Scheduled crawl skipped source '{}': a run of it is already in progress",
                        source);
            }
        }
    }

}
