/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
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

package com.viglet.dumont.connector.plugin.jobs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourcePlugin;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnrichmentChain;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobsSourceProvider;
import com.viglet.dumont.connector.plugin.jobs.source.location.DumJobLocationEnricher;

/**
 * Job-board connector (trabalharei <strong>TRA13</strong>).
 *
 * <p>
 * A thin {@link DumStructuredSourcePlugin} over the structured-source SPI: each configured
 * {@code DumJobSource} is one partner job API (Adzuna, Jooble, BNE, JSearch), fetched through a
 * {@link com.viglet.dumont.connector.plugin.jobs.source.DumJobSourceAdapter} and published to Turing
 * under the {@link DumJobsSchema} contract. The adapters themselves arrive in later tasks; this
 * plugin, the schema, the source store and the seen-id store are the shared contract they plug into.
 *
 * <p>
 * Every record passes through the {@link DumJobLocationEnricher} (trabalharei <strong>TRA20</strong>),
 * the one step that writes the canonical IBGE city and UF; its per-source unresolved count is logged
 * once at the end of each {@link #indexAll(String)} run.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component("jobs")
public class DumJobsPlugin extends DumStructuredSourcePlugin {

    /** Provider name reported for routing and as the document {@code source_apps}. */
    public static final String PROVIDER_NAME = "JOBS";

    private final DumJobsSourceProvider provider;
    private final DumJobLocationEnricher locationEnricher;

    @Autowired
    public DumJobsPlugin(DumConnectorContext dumConnectorContext, DumJobsSourceProvider provider) {
        this(dumConnectorContext, provider, DumJobLocationEnricher.standard());
    }

    public DumJobsPlugin(DumConnectorContext dumConnectorContext, DumJobsSourceProvider provider,
            DumJobLocationEnricher locationEnricher) {
        super(dumConnectorContext, DumEnrichmentChain.of(locationEnricher));
        this.provider = provider;
        this.locationEnricher = locationEnricher;
    }

    @Override
    protected DumStructuredSourceProvider provider() {
        return provider;
    }

    /**
     * The structured run, then one location summary line for the source. DumEnricher has no end-of-run
     * hook, so the plugin closes the count here.
     */
    @Override
    public void indexAll(String source) {
        locationEnricher.reset(source);
        try {
            super.indexAll(source);
        } finally {
            locationEnricher.logSummary(source);
        }
    }

    /** The location step every record of this plugin runs through. */
    public DumJobLocationEnricher locationEnricher() {
        return locationEnricher;
    }
}
