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
import com.viglet.dumont.connector.plugin.jobs.source.contract.DumJobContractEnricher;
import com.viglet.dumont.connector.plugin.jobs.source.location.DumJobLocationEnricher;
import com.viglet.dumont.connector.plugin.jobs.source.salary.DumJobSalaryEnricher;
import com.viglet.dumont.connector.plugin.jobs.source.seniority.DumJobSeniorityEnricher;

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
 * Every record passes through {@link #chain(DumJobLocationEnricher, DumJobSalaryEnricher)}: one
 * canonical step per concern — place ({@link DumJobLocationEnricher}, trabalharei
 * <strong>TRA20</strong>), then salary, contract and seniority ({@link DumJobSalaryEnricher},
 * {@link DumJobContractEnricher}, {@link DumJobSeniorityEnricher}, <strong>TRA21</strong>). The
 * per-source unresolved place and unparsed salary counts are logged once at the end of each
 * {@link #indexAll(String)} run.
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
    private final DumJobSalaryEnricher salaryEnricher;

    @Autowired
    public DumJobsPlugin(DumConnectorContext dumConnectorContext, DumJobsSourceProvider provider) {
        this(dumConnectorContext, provider, DumJobLocationEnricher.standard());
    }

    public DumJobsPlugin(DumConnectorContext dumConnectorContext, DumJobsSourceProvider provider,
            DumJobLocationEnricher locationEnricher) {
        this(dumConnectorContext, provider, locationEnricher, new DumJobSalaryEnricher());
    }

    public DumJobsPlugin(DumConnectorContext dumConnectorContext, DumJobsSourceProvider provider,
            DumJobLocationEnricher locationEnricher, DumJobSalaryEnricher salaryEnricher) {
        super(dumConnectorContext, chain(locationEnricher, salaryEnricher));
        this.provider = provider;
        this.locationEnricher = locationEnricher;
        this.salaryEnricher = salaryEnricher;
    }

    /**
     * The enrichment chain every job record runs through, in order: place, salary, contract,
     * seniority. The steps write disjoint fields (place also writes {@code remote}), so the order
     * only fixes the log and provenance sequence.
     */
    public static DumEnrichmentChain chain(DumJobLocationEnricher locationEnricher,
            DumJobSalaryEnricher salaryEnricher) {
        return DumEnrichmentChain.of(locationEnricher, salaryEnricher, new DumJobContractEnricher(),
                new DumJobSeniorityEnricher());
    }

    @Override
    protected DumStructuredSourceProvider provider() {
        return provider;
    }

    /**
     * The structured run, then one location and one salary summary line for the source.
     * DumEnricher has no end-of-run hook (D100), so the plugin closes the counts here.
     */
    @Override
    public void indexAll(String source) {
        locationEnricher.reset(source);
        salaryEnricher.reset(source);
        try {
            super.indexAll(source);
        } finally {
            locationEnricher.logSummary(source);
            salaryEnricher.logSummary(source);
        }
    }

    /** The location step every record of this plugin runs through. */
    public DumJobLocationEnricher locationEnricher() {
        return locationEnricher;
    }

    /** The salary step every record of this plugin runs through. */
    public DumJobSalaryEnricher salaryEnricher() {
        return salaryEnricher;
    }
}
