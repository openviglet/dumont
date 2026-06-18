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

package com.viglet.dumont.connector.commons.spi.structured;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumContentTypeCoverageRecorder;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageMonitor;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumInMemoryCoverageHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnrichmentChain;
import com.viglet.dumont.connector.commons.spi.structured.resolve.DumCanonicalKey;
import com.viglet.dumont.connector.commons.spi.structured.resolve.DumEntityResolver;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumInMemoryProvenanceHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenance;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenanceHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenanceSnapshot;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumInMemoryPreviewSnapshotStore;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumPreviewSnapshot;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumPreviewSnapshotStore;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumStructuredPreview;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumStructuredPreviewer;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.sn.field.TurSNFieldName;

import lombok.extern.slf4j.Slf4j;

/**
 * Base adapter that turns a {@link DumStructuredSourceProvider} into a {@link DumConnectorPlugin}.
 *
 * <p>
 * It implements the generic indexing loop once — for each configured source it
 * {@link DumStructuredSource#discover() discovers} content ids,
 * {@link DumStructuredSource#extract(String) extracts} each into a typed record, enforces the
 * grounding contract, and publishes a {@link TurSNJobItem} through the shared
 * {@link DumConnectorContext}. Concrete plugins only supply a {@link #provider()}; they never
 * reimplement crawling or the index pipeline.
 *
 * <p>
 * <strong>Enrichment (roadmap D04).</strong> Between extraction and publish each record passes
 * through an optional {@link DumEnrichmentChain} that may derive or normalize fields (canonical
 * taxonomy, region derivation, an LLM-classified label). Enrichment is grounding-safe by
 * construction — contributions are merged through the schema-bound record builder — so the
 * mandatory-field check below runs on the <em>enriched</em> record (a derived field may satisfy a
 * mandatory field), and no enricher can inject an undeclared field or fabricate a value.
 *
 * <p>
 * <strong>Grounding.</strong> Records that miss a mandatory field are skipped (logged), never
 * fabricated. The published attribute map carries only the fields the source actually supplied —
 * {@link DumStructuredRecord} already dropped blanks and empties — so no field reaches the index as
 * {@code ""}. The declared {@link DumStructuredSchema#toAttributeSpecs() schema} travels with every
 * document.
 *
 * <p>
 * <strong>Cross-source resolution (roadmap D18).</strong> When a {@link DumCanonicalKey} is
 * configured, {@link #crawl()} switches from per-source streaming publish to a resolve-then-publish
 * pass: every source's records are accumulated in a {@link DumEntityResolver} and those that share a
 * canonical id are folded — grounding-safe — into one canonical document, published once under its
 * primary source. Because the merge can complete a mandatory field from a second source, the
 * mandatory-field check runs on the <em>merged</em> record. Without a canonical key the per-source
 * behaviour is unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public abstract class DumStructuredSourcePlugin implements DumConnectorPlugin {

    protected final DumConnectorContext dumConnectorContext;
    protected final DumEnrichmentChain enrichmentChain;
    protected final DumPreviewSnapshotStore previewSnapshotStore;
    protected final DumCoverageHistoryStore coverageHistoryStore;
    protected final DumProvenanceHistoryStore provenanceHistoryStore;
    /** Cross-source canonical key (D18); {@code null} disables resolution (per-source publish). */
    protected final DumCanonicalKey canonicalKey;

    protected DumStructuredSourcePlugin(DumConnectorContext dumConnectorContext) {
        this(dumConnectorContext, DumEnrichmentChain.empty(), new DumInMemoryPreviewSnapshotStore(),
                new DumInMemoryCoverageHistoryStore());
    }

    protected DumStructuredSourcePlugin(DumConnectorContext dumConnectorContext,
            DumEnrichmentChain enrichmentChain) {
        this(dumConnectorContext, enrichmentChain, new DumInMemoryPreviewSnapshotStore(),
                new DumInMemoryCoverageHistoryStore());
    }

    protected DumStructuredSourcePlugin(DumConnectorContext dumConnectorContext,
            DumEnrichmentChain enrichmentChain, DumPreviewSnapshotStore previewSnapshotStore) {
        this(dumConnectorContext, enrichmentChain, previewSnapshotStore,
                new DumInMemoryCoverageHistoryStore());
    }

    protected DumStructuredSourcePlugin(DumConnectorContext dumConnectorContext,
            DumEnrichmentChain enrichmentChain, DumPreviewSnapshotStore previewSnapshotStore,
            DumCoverageHistoryStore coverageHistoryStore) {
        this(dumConnectorContext, enrichmentChain, previewSnapshotStore, coverageHistoryStore,
                new DumInMemoryProvenanceHistoryStore());
    }

    protected DumStructuredSourcePlugin(DumConnectorContext dumConnectorContext,
            DumEnrichmentChain enrichmentChain, DumPreviewSnapshotStore previewSnapshotStore,
            DumCoverageHistoryStore coverageHistoryStore,
            DumProvenanceHistoryStore provenanceHistoryStore) {
        this(dumConnectorContext, enrichmentChain, previewSnapshotStore, coverageHistoryStore,
                provenanceHistoryStore, null);
    }

    protected DumStructuredSourcePlugin(DumConnectorContext dumConnectorContext,
            DumEnrichmentChain enrichmentChain, DumPreviewSnapshotStore previewSnapshotStore,
            DumCoverageHistoryStore coverageHistoryStore,
            DumProvenanceHistoryStore provenanceHistoryStore, DumCanonicalKey canonicalKey) {
        this.dumConnectorContext = dumConnectorContext;
        this.enrichmentChain = enrichmentChain != null ? enrichmentChain : DumEnrichmentChain.empty();
        this.previewSnapshotStore = previewSnapshotStore != null ? previewSnapshotStore
                : new DumInMemoryPreviewSnapshotStore();
        this.coverageHistoryStore = coverageHistoryStore != null ? coverageHistoryStore
                : new DumInMemoryCoverageHistoryStore();
        this.provenanceHistoryStore = provenanceHistoryStore != null ? provenanceHistoryStore
                : new DumInMemoryProvenanceHistoryStore();
        this.canonicalKey = canonicalKey;
    }

    /** The provider that resolves this plugin's configured structured sources. */
    protected abstract DumStructuredSourceProvider provider();

    /**
     * Degree of bounded-concurrency extraction used by {@link #indexAll(String)} (roadmap
     * <strong>D31</strong>). The default {@code 1} extracts sequentially — every adapter is unchanged.
     * An HTTP-heavy adapter (AEM, where {@code extract(id)} is one {@code infinity.json} fetch)
     * overrides this so extraction runs on a bounded pool; the publish + coverage/provenance capture
     * stays serial, so the speed-up is grounding/coverage-neutral. See {@link DumBoundedExtraction}.
     */
    protected int extractionConcurrency() {
        return 1;
    }

    @Override
    public String getProviderName() {
        return provider().getProviderName();
    }

    @Override
    public boolean ownsSource(String source) {
        return provider().resolve(source).isPresent();
    }

    @Override
    public void crawl() {
        if (canonicalKey == null) {
            provider().sourceNames().forEach(this::indexAll);
        } else {
            resolveAndIndex();
        }
    }

    /**
     * Cross-source entity resolution (roadmap <strong>D18</strong>): accumulate every source's
     * (extracted, enriched) records in a {@link DumEntityResolver}, then publish each resolved entity
     * once. Records sharing a {@link #canonicalKey canonical id} are folded — grounding-safe — into a
     * single canonical document under its primary source's session; the mandatory-field check runs on
     * the merged record, so a field a second source supplied can satisfy it. A record with no
     * canonical id is published on its own, unmerged.
     */
    private void resolveAndIndex() {
        DumEntityResolver resolver = new DumEntityResolver(canonicalKey);
        provider().sourceNames().forEach(sourceName -> provider().resolve(sourceName)
                .ifPresent(source -> source.discover().forEach(contentId -> source.extract(contentId)
                        .map(record -> enrichmentChain.apply(source, record))
                        .ifPresent(record -> resolver.add(source, record)))));
        Map<String, DumConnectorSession> sessions = new LinkedHashMap<>();
        for (DumEntityResolver.Entity entity : resolver.entities()) {
            DumStructuredRecord record = entity.record();
            List<String> missing = record.missingMandatoryFields();
            if (!missing.isEmpty()) {
                log.warn("Skipping canonical entity '{}' — missing mandatory field(s) after merge: {}",
                        record.id(), missing);
                continue;
            }
            DumStructuredSource primary = entity.source();
            DumConnectorSession session =
                    sessions.computeIfAbsent(primary.getSourceName(), name -> newSession(primary));
            dumConnectorContext.addJobItem(new DumJobItemWithSession(
                    toJobItem(primary, record), session, Set.of(), false));
        }
        sessions.values().forEach(session -> dumConnectorContext.finishIndexing(session, false));
    }

    @Override
    public void indexAll(String source) {
        provider().resolve(source).ifPresentOrElse(structuredSource -> {
            DumConnectorSession session = newSession(structuredSource);
            // Capture a fingerprint of every published document so a later dry-run preview (D14) can
            // diff against the last full index. Only indexAll captures it — a partial indexById run
            // would otherwise mark the rest of the catalog as removed.
            DumPreviewSnapshot.Builder snapshot = DumPreviewSnapshot.builder(structuredSource.schema());
            // Per-field provenance of this run (D16), accumulated alongside the fingerprint so a later
            // run can answer "when was each field last seen" — no extra pass over the source.
            DumProvenanceSnapshot.Builder provenance =
                    DumProvenanceSnapshot.builder(structuredSource.schema());
            // Per content-type coverage/provenance (D25): a typed source buckets each record by its
            // resolved content type so drift reads per type, additively to the source-level series.
            DumContentTypeCoverageRecorder byType = new DumContentTypeCoverageRecorder();
            // Bounded-concurrency extraction (roadmap D31): extract(id) may run on a small pool while
            // the consumer below — publish + snapshot/coverage/provenance capture — stays serial on
            // this thread, so capture remains single-threaded and order-independent. Default
            // extractionConcurrency() == 1 keeps every adapter sequential.
            DumBoundedExtraction.extractEach(structuredSource, structuredSource.discover(),
                    extractionConcurrency(),
                    record -> publish(structuredSource, enrichmentChain.apply(structuredSource, record),
                            session, snapshot, provenance, byType));
            dumConnectorContext.finishIndexing(session, false);
            DumPreviewSnapshot baseline = snapshot.build();
            previewSnapshotStore.save(source, baseline);
            // Per-field coverage of this run, appended to the source's history so a later run can be
            // checked for drift (D15) — derived from the same fingerprint, no extra pass over the source.
            coverageHistoryStore.record(source, baseline.documents().size(),
                    DumCoverageMonitor.coverage(structuredSource.schema(), baseline));
            provenanceHistoryStore.record(source, provenance.documentCount(), provenance.fields());
            byType.flush(source, coverageHistoryStore, provenanceHistoryStore);
        }, () -> log.error("Structured source '{}' not found", source));
    }

    @Override
    public void indexById(String source, List<String> contentId) {
        provider().resolve(source).ifPresentOrElse(structuredSource -> {
            DumConnectorSession session = newSession(structuredSource);
            contentId.forEach(id -> indexOne(structuredSource, id, session, null, null, null));
            dumConnectorContext.finishIndexing(session, true);
        }, () -> log.error("Structured source '{}' not found", source));
    }

    /**
     * Dry-run preview (roadmap <strong>D14</strong>): extract a bounded sample of {@code sampleSize}
     * records, validate them against the declared schema, and diff them against the last indexed
     * baseline — <strong>writing nothing</strong>. Returns {@code null} when the source is unknown.
     */
    public DumStructuredPreview preview(String source, int sampleSize) {
        return provider().resolve(source)
                .map(structuredSource -> DumStructuredPreviewer.preview(structuredSource, sampleSize,
                        enrichmentChain,
                        previewSnapshotStore.load(source).orElseGet(DumPreviewSnapshot::empty)))
                .orElse(null);
    }

    @Override
    public List<String> discoverContentIds(String source) {
        return provider().resolve(source)
                .map(DumStructuredSource::discover)
                .orElseGet(List::of);
    }

    @Override
    public Locale resolveLocale(String source, String objectId) {
        return provider().resolve(source)
                .map(DumStructuredSource::getLocale)
                .orElse(null);
    }

    private void indexOne(DumStructuredSource source, String contentId, DumConnectorSession session,
            DumPreviewSnapshot.Builder snapshot, DumProvenanceSnapshot.Builder provenance,
            DumContentTypeCoverageRecorder byType) {
        source.extract(contentId).ifPresentOrElse(
                record -> publish(source, enrichmentChain.apply(source, record), session, snapshot,
                        provenance, byType),
                () -> log.warn("No record extracted for id '{}' in source '{}'", contentId,
                        source.getSourceName()));
    }

    private void publish(DumStructuredSource source, DumStructuredRecord record,
            DumConnectorSession session, DumPreviewSnapshot.Builder snapshot,
            DumProvenanceSnapshot.Builder provenance, DumContentTypeCoverageRecorder byType) {
        List<String> missing = record.missingMandatoryFields();
        if (!missing.isEmpty()) {
            log.warn("Skipping record '{}' in source '{}' — missing mandatory field(s): {}",
                    record.id(), source.getSourceName(), missing);
            return;
        }
        dumConnectorContext.addJobItem(new DumJobItemWithSession(
                toJobItem(source, record), session, Set.of(), false));
        if (snapshot != null) {
            snapshot.add(record.id(), record.values());
        }
        if (provenance != null) {
            provenance.add(record.values(), record.provenance());
        }
        if (byType != null) {
            byType.add(record);
        }
    }

    /**
     * Build the {@link TurSNJobItem} for a record: grounded attributes (plus the record id under the
     * standard {@code id} field) and the declared schema specs. When the record carries field-level
     * provenance (roadmap D16), a compact reserved {@code dum_provenance} attribute + spec are added
     * so the search/RAG side can cite and audit where each attribute came from. Subclasses may
     * override to add provider-specific attributes (e.g. {@code source_apps}).
     */
    protected TurSNJobItem toJobItem(DumStructuredSource source, DumStructuredRecord record) {
        Map<String, Object> attributes = new HashMap<>(record.values());
        attributes.putIfAbsent(TurSNFieldName.ID, record.id());
        projectEmbeddingFields(source.schema(), attributes);
        List<TurSNJobAttributeSpec> specs = new ArrayList<>(source.schema().toAttributeSpecs());
        DumProvenance.contribute(source.getSourceName(), record, attributes, specs);
        return new TurSNJobItem(TurSNJobAction.CREATE,
                new ArrayList<>(source.getSiteNames()),
                source.getLocale(),
                attributes,
                specs);
    }

    /**
     * Roadmap <strong>D17</strong>: project fields that declare an {@link DumEmbeddingRole} onto the
     * canonical Turing keys ({@code title}/{@code abstract}/{@code text}) that the RAG partitioner
     * reads, so a source whose semantic content lives under custom field names still embeds well at
     * Turing's index time. The canonical keys are core SN fields, so no extra attribute spec is
     * needed — only the value is projected.
     *
     * <p>
     * Grounding-safe by construction: only a present value is copied ({@code record.values()} has
     * already dropped blanks); an explicitly-supplied canonical key always wins ({@code putIfAbsent});
     * and when the schema declares the canonical field itself the mapping is 1:1, so projection is
     * skipped. The source field is kept as-is for display/facets — nothing is renamed or fabricated.
     * If two fields claim the same role, the first declared wins. The contract is documented in
     * {@code docs/EMBEDDING_PARTITION_CONTRACT.md}.
     */
    private static void projectEmbeddingFields(DumStructuredSchema schema,
            Map<String, Object> attributes) {
        for (DumStructuredField field : schema.fields()) {
            DumEmbeddingRole role = field.embeddingRole();
            if (!role.projects()) {
                continue;
            }
            String target = role.targetAttribute().orElseThrow();
            if (schema.hasField(target)) {
                continue;
            }
            Object value = attributes.get(field.name());
            if (value != null) {
                attributes.putIfAbsent(target, value);
            }
        }
    }

    private DumConnectorSession newSession(DumStructuredSource source) {
        return new DumConnectorSession(source.getSourceName(), source.getProviderName(),
                source.getLocale());
    }
}
