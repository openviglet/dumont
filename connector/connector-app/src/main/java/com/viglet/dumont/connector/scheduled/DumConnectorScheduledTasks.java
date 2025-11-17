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

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.service.DumConnectorConfigVarService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DumConnectorScheduledTasks {
    private final DumConnectorPlugin dumConnectorPlugin;
    private final DumConnectorConfigVarService configVarService;

    public DumConnectorScheduledTasks(DumConnectorPlugin dumConnectorPlugin,
            DumConnectorConfigVarService configVarService) {
        this.dumConnectorPlugin = dumConnectorPlugin;
        this.configVarService = configVarService;
    }

    @Scheduled(cron = "${dumont.connector.cron:-}", zone = "${dumont.connector.cron.zone:UTC}")
    public void executeWebCrawler() {
        if (configVarService.hasNotFirstTime()) {
            log.info("This is the first time, waiting next schedule.");
        } else {
            dumConnectorPlugin.crawl();
        }
    }

}
