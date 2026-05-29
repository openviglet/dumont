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

package com.viglet.dumont.connector.plugin.eds;

import java.util.List;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSSourceRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Primary
@Component("eds")
public class DumEDSPlugin implements DumConnectorPlugin {

    public static final String PROVIDER_NAME = "EDS";

    private final DumEDSSourceRepository dumEDSSourceRepository;
    private final DumEDSPluginProcess dumEDSPluginProcess;

    public DumEDSPlugin(DumEDSSourceRepository dumEDSSourceRepository,
            DumEDSPluginProcess dumEDSPluginProcess) {
        this.dumEDSSourceRepository = dumEDSSourceRepository;
        this.dumEDSPluginProcess = dumEDSPluginProcess;
    }

    @Override
    public void crawl() {
        dumEDSSourceRepository.findAll().forEach(dumEDSPluginProcess::start);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public void indexAll(String source) {
        dumEDSSourceRepository.findByTitle(source).ifPresent(dumEDSPluginProcess::start);
    }

    @Override
    public void indexById(String source, List<String> contentId) {
        throw new UnsupportedOperationException(
                "EDS plugin does not support indexing by ID — discovery is driven by query-index.json");
    }

    @Override
    public List<String> discoverContentIds(String source) {
        throw new UnsupportedOperationException(
                "EDS plugin does not expose content IDs — discovery yields URL paths");
    }
}
