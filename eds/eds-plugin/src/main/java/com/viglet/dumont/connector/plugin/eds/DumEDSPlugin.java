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
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSSourceRepository;
import com.viglet.dumont.connector.plugin.eds.state.DumEDSCrawlStateService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component("eds")
public class DumEDSPlugin implements DumConnectorPlugin {

    public static final String PROVIDER_NAME = "EDS";

    private final DumEDSSourceRepository dumEDSSourceRepository;
    private final DumEDSPluginProcess dumEDSPluginProcess;
    private final DumEDSCrawlStateService crawlStateService;

    public DumEDSPlugin(DumEDSSourceRepository dumEDSSourceRepository,
            DumEDSPluginProcess dumEDSPluginProcess, DumEDSCrawlStateService crawlStateService) {
        this.dumEDSSourceRepository = dumEDSSourceRepository;
        this.dumEDSPluginProcess = dumEDSPluginProcess;
        this.crawlStateService = crawlStateService;
    }

    /** D345: without its crawl state, the next run fetches every page instead of skipping unchanged ones. */
    @Override
    public void resetState(String source) {
        find(source).ifPresent(crawlStateService::deleteAll);
    }

    /**
     * D349: the source a connector-level call names. Its runs file the ledger under the id, so the
     * id is the name the console passes; the title is still accepted for callers written before.
     */
    private Optional<DumEDSSource> find(String source) {
        if (source == null) {
            return Optional.empty();
        }
        return dumEDSSourceRepository.findById(source).or(() -> dumEDSSourceRepository.findByTitle(source));
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
    public boolean ownsSource(String source) {
        return find(source).isPresent();
    }

    @Override
    public void indexAll(String source) {
        find(source).ifPresent(dumEDSPluginProcess::start);
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
