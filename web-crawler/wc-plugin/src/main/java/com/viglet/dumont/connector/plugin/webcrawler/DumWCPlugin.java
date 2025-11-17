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

package com.viglet.dumont.connector.plugin.webcrawler;

import java.util.List;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCSourceRepository;

@Primary
@Component
public class DumWCPlugin implements DumConnectorPlugin {
    private final DumWCSourceRepository dumWCSourceRepository;
    private final DumWCPluginProcess dumWCPluginProcess;

    public DumWCPlugin(DumWCSourceRepository dumWCSourceRepository, DumWCPluginProcess dumWCPluginProcess) {
        this.dumWCSourceRepository = dumWCSourceRepository;
        this.dumWCPluginProcess = dumWCPluginProcess;
    }

    @Override
    public void crawl() {
        dumWCSourceRepository.findAll().forEach(dumWCPluginProcess::start);
    }

    @Override
    public String getProviderName() {
        return "WEB-CRAWLER";
    }

    @Override
    public void indexAll(String source) {
        throw new UnsupportedOperationException("This method is only a placeholder");
    }

    @Override
    public void indexById(String source, List<String> contentId) {
        throw new UnsupportedOperationException("This method is only a placeholder");
    }
}