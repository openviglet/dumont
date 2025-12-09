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

package com.viglet.dumont.connector.indexing;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.plugin.DumIndexingPlugin;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.client.sn.job.TurSNJobUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Indexing plugin for Viglet Turing ES
 * 
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "dumont.indexing.provider", havingValue = "turing", matchIfMissing = true)
public class DumTuringIndexingPlugin implements DumIndexingPlugin {
    
    private final String turingUrl;
    private final String turingApiKey;

    public DumTuringIndexingPlugin(@Value("${turing.url}") String turingUrl,
            @Value("${turing.apiKey}") String turingApiKey) {
        this.turingUrl = turingUrl;
        this.turingApiKey = turingApiKey;
        log.info("Initialized Turing indexing plugin with URL: {}", turingUrl);
    }

    @Override
    public void index(TurSNJobItems turSNJobItems) {
        log.debug("Indexing {} items to Turing ES", turSNJobItems.getTuringDocuments().size());
        TurSNJobUtils.importItems(turSNJobItems, getTurSNServer(), false);
    }

    @Override
    public String getProviderName() {
        return "TURING";
    }

    private TurSNServer getTurSNServer() {
        return new TurSNServer(URI.create(turingUrl), null,
                new TurApiKeyCredentials(turingApiKey));
    }
}
