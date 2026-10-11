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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourcePlugin;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumClassifier;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnrichmentChain;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobCallRepository;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobOccupationCacheRepository;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobsSourceProvider;
import com.viglet.dumont.connector.plugin.jobs.source.contract.DumJobContractEnricher;
import com.viglet.dumont.connector.plugin.jobs.source.location.DumJobLocationEnricher;
import com.viglet.dumont.connector.plugin.jobs.source.occupation.DumJobOccupationEnricher;
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
 * Every record passes through
 * {@link #chain(DumJobLocationEnricher, DumJobSalaryEnricher, DumJobOccupationEnricher)}: one
 * canonical step per concern — place ({@link DumJobLocationEnricher}, trabalharei
 * <strong>TRA20</strong>), then salary, contract and seniority ({@link DumJobSalaryEnricher},
 * {@link DumJobContractEnricher}, {@link DumJobSeniorityEnricher}, <strong>TRA21</strong>), then
 * occupation ({@link DumJobOccupationEnricher}, <strong>TRA22</strong>). The per-source place, salary
 * and occupation counts are logged once at the end of each {@link #indexAll(String)} run.
 *
 * <p>
 * <strong>Occupation fallback.</strong> The occupation step asks a commons {@link DumClassifier} for
 * the titles its dictionary does not read when a bean named
 * {@value DumJobOccupationEnricher#CLASSIFIER_BEAN} exists, at most
 * {@code dumont.jobs.occupation.classifier.daily-cap} calls per rolling 24 hours (default
 * {@value DumJobOccupationEnricher#DEFAULT_DAILY_CAP}). Dumont declares no such bean today: its only
 * LLM classifier, {@code DumTuringClassifier}, lives in {@code connector-app} with no bean of its own,
 * so the dictionary alone runs.
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
    private final DumJobOccupationEnricher occupationEnricher;

    @Autowired
    public DumJobsPlugin(DumConnectorContext dumConnectorContext, DumJobsSourceProvider provider,
            DumJobOccupationCacheRepository occupationCache, DumJobCallRepository callRepository,
            @Qualifier(DumJobOccupationEnricher.CLASSIFIER_BEAN) ObjectProvider<DumClassifier> classifier,
            @Value("${dumont.jobs.occupation.classifier.daily-cap:"
                    + DumJobOccupationEnricher.DEFAULT_DAILY_CAP + "}") int dailyCap) {
        this(dumConnectorContext, provider, DumJobLocationEnricher.standard(), new DumJobSalaryEnricher(),
                DumJobOccupationEnricher.standard(classifier.getIfAvailable(), occupationCache,
                        callRepository, dailyCap));
    }

    /** The plugin with the dictionary-only occupation step: no classifier, nothing persisted by it. */
    public DumJobsPlugin(DumConnectorContext dumConnectorContext, DumJobsSourceProvider provider) {
        this(dumConnectorContext, provider, DumJobLocationEnricher.standard());
    }

    public DumJobsPlugin(DumConnectorContext dumConnectorContext, DumJobsSourceProvider provider,
            DumJobLocationEnricher locationEnricher) {
        this(dumConnectorContext, provider, locationEnricher, new DumJobSalaryEnricher());
    }

    public DumJobsPlugin(DumConnectorContext dumConnectorContext, DumJobsSourceProvider provider,
            DumJobLocationEnricher locationEnricher, DumJobSalaryEnricher salaryEnricher) {
        this(dumConnectorContext, provider, locationEnricher, salaryEnricher,
                DumJobOccupationEnricher.dictionaryOnly());
    }

    public DumJobsPlugin(DumConnectorContext dumConnectorContext, DumJobsSourceProvider provider,
            DumJobLocationEnricher locationEnricher, DumJobSalaryEnricher salaryEnricher,
            DumJobOccupationEnricher occupationEnricher) {
        super(dumConnectorContext, chain(locationEnricher, salaryEnricher, occupationEnricher));
        this.provider = provider;
        this.locationEnricher = locationEnricher;
        this.salaryEnricher = salaryEnricher;
        this.occupationEnricher = occupationEnricher;
    }

    /**
     * {@link #chain(DumJobLocationEnricher, DumJobSalaryEnricher, DumJobOccupationEnricher)} with the
     * dictionary-only occupation step.
     */
    public static DumEnrichmentChain chain(DumJobLocationEnricher locationEnricher,
            DumJobSalaryEnricher salaryEnricher) {
        return chain(locationEnricher, salaryEnricher, DumJobOccupationEnricher.dictionaryOnly());
    }

    /**
     * The enrichment chain every job record runs through, in order: place, salary, contract,
     * seniority, occupation. The steps write disjoint fields (place also writes {@code remote}), so
     * the order only fixes the log and provenance sequence.
     */
    public static DumEnrichmentChain chain(DumJobLocationEnricher locationEnricher,
            DumJobSalaryEnricher salaryEnricher, DumJobOccupationEnricher occupationEnricher) {
        return DumEnrichmentChain.of(locationEnricher, salaryEnricher, new DumJobContractEnricher(),
                new DumJobSeniorityEnricher(), occupationEnricher);
    }

    @Override
    protected DumStructuredSourceProvider provider() {
        return provider;
    }

    /**
     * The structured run, then one location, one salary and one occupation summary line for the
     * source. DumEnricher has no end-of-run hook (D100), so the plugin closes the counts here.
     */
    @Override
    public void indexAll(String source) {
        locationEnricher.reset(source);
        salaryEnricher.reset(source);
        occupationEnricher.reset(source);
        try {
            super.indexAll(source);
        } finally {
            locationEnricher.logSummary(source);
            salaryEnricher.logSummary(source);
            occupationEnricher.logSummary(source);
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

    /** The occupation step every record of this plugin runs through. */
    public DumJobOccupationEnricher occupationEnricher() {
        return occupationEnricher;
    }
}
