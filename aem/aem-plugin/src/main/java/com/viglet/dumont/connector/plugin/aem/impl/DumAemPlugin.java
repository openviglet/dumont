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

package com.viglet.dumont.connector.plugin.aem.impl;

import static com.viglet.dumont.connector.aem.commons.DumAemConstants.SITE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourcePlugin;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageMonitor;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumPreviewSnapshot;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumPreviewSnapshotStore;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenanceHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenanceSnapshot;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.service.DumAemObjectService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSessionService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSourceService;
import com.viglet.dumont.connector.plugin.aem.source.DumAemStructuredSource;
import com.viglet.dumont.connector.plugin.aem.source.DumAemStructuredSourceProvider;
import com.viglet.turing.client.sn.job.TurSNJobItem;

import lombok.extern.slf4j.Slf4j;

/**
 * AEM connector — adapted to the structured-source SPI (roadmap <strong>D21</strong>).
 *
 * <p>
 * The AEM source indexes through the generic structured loop: a {@link DumAemStructuredSourceProvider}
 * resolves each source's environment(s) as {@link DumAemStructuredSource}s (schema from the attribute
 * specs, {@code discover()} over the path/QueryBuilder enumeration, {@code extract(id)} reusing the
 * connector's existing field extraction), and the shared loop grounds, previews (D14) and tracks
 * field coverage/drift (D15) and provenance (D16) for free — wired to the same persistent stores the
 * monitoring API reads.
 *
 * <p>
 * <strong>Author/publish + delta stay intact.</strong> Each adapter extracts <em>one</em>
 * environment; {@link #indexAll(String)} drives every enabled environment of a source under a single
 * indexing session, stamping each job item with its environment, the per-record delta checksum and
 * the resolved locale ({@link #toJobItem}). The tracking-driven deindex sweep in
 * {@code finishIndexing} removes stale/unpublished content as before. Coverage/preview/provenance are
 * captured once, from the primary environment.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component("aem")
public class DumAemPlugin extends DumStructuredSourcePlugin {

    private final DumAemStructuredSourceProvider provider;
    private final DumAemSourceService sourceService;
    private final DumAemSessionService sessionService;
    private final DumAemObjectService objectService;

    public DumAemPlugin(DumConnectorContext dumConnectorContext,
            DumAemStructuredSourceProvider provider, DumAemSourceService sourceService,
            DumAemSessionService sessionService, DumAemObjectService objectService,
            DumPreviewSnapshotStore previewSnapshotStore,
            DumCoverageHistoryStore coverageHistoryStore,
            DumProvenanceHistoryStore provenanceHistoryStore) {
        super(dumConnectorContext, null, previewSnapshotStore, coverageHistoryStore,
                provenanceHistoryStore);
        this.provider = provider;
        this.sourceService = sourceService;
        this.sessionService = sessionService;
        this.objectService = objectService;
    }

    @Override
    protected DumStructuredSourceProvider provider() {
        return provider;
    }

    /**
     * Index every enabled environment of a source under one session (so the deindex sweep in
     * {@code finishIndexing} sees both), capturing coverage/preview/provenance once from the primary
     * environment. Asynchronous so the connector API returns immediately, as the legacy flow did.
     */
    @Async
    @Override
    public void indexAll(String source) {
        sourceService.getDumAemSourceByName(source).ifPresentOrElse(
                aemSource -> indexAllEnvironments(source, aemSource),
                () -> log.error("AEM source '{}' not found", source));
    }

    private void indexAllEnvironments(String source, DumAemSource aemSource) {
        List<DumAemEnv> environments = provider.enabledEnvironments(aemSource);
        if (environments.isEmpty()) {
            log.warn("AEM source '{}' has no enabled environment (author/publish) — nothing to index",
                    source);
            return;
        }
        DumConnectorSession session = newSession(aemSource);
        DumAemEnv primaryEnv = environments.getFirst();
        DumAemStructuredSource primary = provider.adapter(aemSource, primaryEnv);
        DumStructuredSchema schema = primary.schema();
        DumPreviewSnapshot.Builder snapshot = DumPreviewSnapshot.builder(schema);
        DumProvenanceSnapshot.Builder provenance = DumProvenanceSnapshot.builder(schema);
        List<String> contentIds = primary.discover();
        for (DumAemEnv environment : environments) {
            DumAemStructuredSource structuredSource = environment == primaryEnv ? primary
                    : provider.adapter(aemSource, environment);
            boolean capture = environment == primaryEnv;
            contentIds.forEach(id -> structuredSource.extract(id)
                    .ifPresent(extracted -> publish(structuredSource,
                            enrichmentChain.apply(structuredSource, extracted), session,
                            capture ? snapshot : null, capture ? provenance : null)));
        }
        dumConnectorContext.finishIndexing(session, false);
        DumPreviewSnapshot baseline = snapshot.build();
        previewSnapshotStore.save(source, baseline);
        coverageHistoryStore.record(source, baseline.documents().size(),
                DumCoverageMonitor.coverage(schema, baseline));
        provenanceHistoryStore.record(source, provenance.documentCount(), provenance.fields());
    }

    /** Index specific content ids across every enabled environment (no deindex sweep). */
    @Override
    public void indexById(String source, List<String> contentId) {
        sourceService.getDumAemSourceByName(source).ifPresentOrElse(aemSource -> {
            DumConnectorSession session = newSession(aemSource);
            for (DumAemEnv environment : provider.enabledEnvironments(aemSource)) {
                DumAemStructuredSource structuredSource = provider.adapter(aemSource, environment);
                contentId.forEach(id -> structuredSource.extract(id)
                        .ifPresent(extracted -> publish(structuredSource,
                                enrichmentChain.apply(structuredSource, extracted), session, null, null)));
            }
            dumConnectorContext.finishIndexing(session, true);
        }, () -> log.error("AEM source '{}' not found", source));
    }

    private void publish(DumAemStructuredSource source, DumStructuredRecord structuredRecord,
            DumConnectorSession session, DumPreviewSnapshot.Builder snapshot,
            DumProvenanceSnapshot.Builder provenance) {
        List<String> missing = structuredRecord.missingMandatoryFields();
        if (!missing.isEmpty()) {
            log.warn("Skipping record '{}' in AEM source '{}' — missing mandatory field(s): {}",
                    structuredRecord.id(), source.getSourceName(), missing);
            return;
        }
        dumConnectorContext.addJobItem(new DumJobItemWithSession(toJobItem(source, structuredRecord),
                session, Set.of(), false));
        if (snapshot != null) {
            snapshot.add(structuredRecord.id(), structuredRecord.values());
        }
        if (provenance != null) {
            provenance.add(structuredRecord.values(), structuredRecord.provenance());
        }
    }

    /**
     * Stamp the AEM-specific document fields the legacy job pipeline produced: the environment, the
     * per-record delta checksum and resolved locale, and the {@code site} attribute. Sites/locale
     * default come from the bound environment via {@link #toJobItem(DumStructuredSource,
     * DumStructuredRecord) super}; the per-record locale overrides it for multi-locale sources.
     */
    @Override
    protected TurSNJobItem toJobItem(DumStructuredSource source, DumStructuredRecord structuredRecord) {
        TurSNJobItem item = super.toJobItem(source, structuredRecord);
        if (source instanceof DumAemStructuredSource aemSource) {
            item.setEnvironment(aemSource.getEnvironment().toString());
            aemSource.checksum(structuredRecord.id()).ifPresent(item::setChecksum);
            aemSource.locale(structuredRecord.id()).ifPresent(item::setLocale);
            aemSource.contentSiteName()
                    .ifPresent(site -> item.getAttributes().putIfAbsent(SITE, site));
        }
        return item;
    }

    @Override
    public Locale resolveLocale(String source, String objectId) {
        return sourceService.getDumAemSourceByName(source)
                .map(aemSource -> DumAemCommonsUtils.getLocaleByPath(
                        sessionService.getDumAemSession(aemSource, false).getConfiguration(), objectId))
                .orElse(null);
    }

    @Override
    public List<EnvironmentInfo> resolveEnvironments(String source, String objectId) {
        return sourceService.getDumAemSourceByName(source).map(aemSource -> {
            DumAemConfiguration config = sessionService.getDumAemSession(aemSource, false)
                    .getConfiguration();
            return DumAemCommonsUtils.getInfinityJson(objectId, config, false).map(infinityJson -> {
                List<EnvironmentInfo> environments = new ArrayList<>();
                if (config.isAuthor()) {
                    environments.add(new EnvironmentInfo(DumAemEnv.AUTHOR.toString(),
                            List.of(config.getAuthorSNSite())));
                }
                if (config.isPublish()) {
                    DumAemObjectGeneric aemObject = objectService.getDumAemObjectGeneric(objectId,
                            infinityJson, DumAemEvent.INDEXING);
                    if (aemObject.isDelivered()) {
                        environments.add(new EnvironmentInfo(DumAemEnv.PUBLISHING.toString(),
                                List.of(config.getPublishSNSite())));
                    }
                }
                return environments;
            }).orElse(Collections.emptyList());
        }).orElse(Collections.emptyList());
    }

    private DumConnectorSession newSession(DumAemSource aemSource) {
        return new DumConnectorSession(aemSource.getName(), getProviderName(),
                aemSource.getDefaultLocale() != null ? aemSource.getDefaultLocale() : Locale.US);
    }
}
