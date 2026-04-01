/*
 * Copyright (C) 2016-2024 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.db;

import java.util.List;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.plugin.db.persistence.repository.DumDbSourceRepository;

@Primary
@Component("db")
public class DumDbPlugin implements DumConnectorPlugin {

    private final DumDbSourceRepository dumDbSourceRepository;
    private final DumDbPluginProcess dumDbPluginProcess;

    public DumDbPlugin(DumDbSourceRepository dumDbSourceRepository, DumDbPluginProcess dumDbPluginProcess) {
        this.dumDbSourceRepository = dumDbSourceRepository;
        this.dumDbPluginProcess = dumDbPluginProcess;
    }

    @Override
    public void crawl() {
        dumDbSourceRepository.findAll().forEach(dumDbPluginProcess::start);
    }

    @Override
    public String getProviderName() {
        return "JDBC-DATABASE";
    }

    @Override
    public void indexAll(String source) {
        dumDbSourceRepository.findByName(source).ifPresent(dumDbPluginProcess::start);
    }

    @Override
    public void indexById(String source, List<String> contentId) {
        throw new UnsupportedOperationException("DB plugin does not support indexing by ID");
    }

    @Override
    public List<String> discoverContentIds(String source) {
        throw new UnsupportedOperationException("DB plugin does not support content ID discovery");
    }
}
