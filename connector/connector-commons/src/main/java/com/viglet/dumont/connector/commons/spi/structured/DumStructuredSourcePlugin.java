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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumIndexingSchema;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumContentTypeCoverageRecorder;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageMonitor;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumInMemoryCoverageHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnrichmentChain;
import com.viglet.dumont.connector.commons.spi.structured.resolve.DumCanonicalKey;
import com.viglet.dumont.connector.commons.spi.structured.resolve.DumInMemoryResolutionIndex;
import com.viglet.dumont.connector.commons.spi.structured.resolve.DumResolutionIndex;
import com.viglet.dumont.connector.commons.spi.structured.resolve.DumResolutionRun;
import com.viglet.dumont.connector.commons.spi.structured.resolve.DumSurvivorship;
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
 * <strong>Removals (roadmap D113).</strong> Every session the plugin opens reports each document the
 * connector deindexes under it to {@link DumStructuredSource#deindexed} and to the enrichers, so a
 * source reacts to the sweep's decision instead of re-deriving it.
 *
 * <p>
 * <strong>Cross-source resolution (roadmap D18, D109).</strong> When a {@link DumCanonicalKey} is
 * configured, every {@link #indexAll(String)} run files its records in a {@link DumResolutionIndex}
 * shared by every source of the provider and across runs, and publishes each keyed record as the
 * canonical document its {@link #survivorship()} rule (D110) makes — grounding-safe — from every
 * live member any source has filed. Each
 * source holding a member publishes the document under its own session, so no source's deindex sweep
 * removes a document another source still carries; it leaves the index once no member is live.
 * Because the merge can complete a mandatory field from a second source, the mandatory-field check
 * runs on the <em>merged</em> record. {@link #crawl()} is {@code indexAll} for each source. Without a
 * canonical key the per-source behaviour is unchanged.
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
    /** Why a run whose discovery was cut short stopped (D96, D115). */
    private static final String DISCOVERY_STOPPED = "discovery stopped before the end of the source";
    /** The canonical-id index every run consults (D109); {@code null} without a canonical key. */
    protected final DumResolutionIndex resolutionIndex;

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
        this(dumConnectorContext, enrichmentChain, previewSnapshotStore, coverageHistoryStore,
                provenanceHistoryStore, canonicalKey, null);
    }

    /**
     * @param resolutionIndex the canonical-id index (D109); {@code null} keeps one in memory when a
     *                        canonical key is given
     */
    protected DumStructuredSourcePlugin(DumConnectorContext dumConnectorContext,
            DumEnrichmentChain enrichmentChain, DumPreviewSnapshotStore previewSnapshotStore,
            DumCoverageHistoryStore coverageHistoryStore,
            DumProvenanceHistoryStore provenanceHistoryStore, DumCanonicalKey canonicalKey,
            DumResolutionIndex resolutionIndex) {
        this.dumConnectorContext = dumConnectorContext;
        this.enrichmentChain = enrichmentChain != null ? enrichmentChain : DumEnrichmentChain.empty();
        this.previewSnapshotStore = previewSnapshotStore != null ? previewSnapshotStore
                : new DumInMemoryPreviewSnapshotStore();
        this.coverageHistoryStore = coverageHistoryStore != null ? coverageHistoryStore
                : new DumInMemoryCoverageHistoryStore();
        this.provenanceHistoryStore = provenanceHistoryStore != null ? provenanceHistoryStore
                : new DumInMemoryProvenanceHistoryStore();
        this.canonicalKey = canonicalKey;
        if (canonicalKey == null) {
            this.resolutionIndex = null;
        } else {
            this.resolutionIndex = resolutionIndex != null ? resolutionIndex : new DumInMemoryResolutionIndex();
        }
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

    /**
     * Per-extract wall-clock bound in milliseconds used by {@link #indexAll(String)} (roadmap
     * <strong>D34</strong>). The default {@code 0} disables it — no extraction is ever cancelled for
     * time, exactly as before. An HTTP-heavy adapter (AEM) overrides this so a single hung
     * {@code infinity.json} fetch past the HTTP client's own timeout cannot stall the whole run: the
     * overrun is cancelled and counted as a failed extraction. See {@link DumBoundedExtraction}.
     */
    protected long extractionTimeoutMs() {
        return 0L;
    }

    /**
     * Failure-wave threshold (roadmap <strong>D34</strong>): when the fraction of <em>failed</em>
     * extractions over an otherwise-complete run reaches this ratio, the run finishes
     * <em>standalone</em> (no deindex) so a transient outage that dropped many ids keeps the prior
     * index instead of mass-deindexing what it could not re-fetch. The default {@code 0.5} guards
     * only a genuine collapse; {@code <= 0} disables the guard. Generalises D09 / D33 from the
     * zero-yield and interrupted cases to a partial collapse.
     */
    protected double failureRatioThreshold() {
        return 0.5;
    }

    /**
     * Whether {@code outcome} represents a failure wave (roadmap <strong>D34</strong>) that should
     * finish the run standalone: the guard is enabled ({@link #failureRatioThreshold()} {@code > 0}),
     * at least one extraction failed, and the failure ratio met the threshold.
     */
    protected boolean isFailureWave(DumExtractionOutcome outcome) {
        double threshold = failureRatioThreshold();
        return threshold > 0 && outcome.failed() > 0 && outcome.failureRatio() >= threshold;
    }

    /** Human-readable reason for a failure-wave standalone finish (roadmap <strong>D34</strong>). */
    protected String failureWaveReason(DumExtractionOutcome outcome) {
        return "failure wave — %d of %d extractions failed (%.0f%% ≥ threshold %.0f%%)".formatted(
                outcome.failed(), outcome.processed(), outcome.failureRatio() * 100,
                failureRatioThreshold() * 100);
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
        provider().sourceNames().forEach(this::indexAll);
    }

    /**
     * How the live members of a canonical id become its document (roadmap <strong>D110</strong>). The
     * default is {@link DumSurvivorship#fieldMerge()}, the D18 rule; a provider that must keep one
     * partner's record whole overrides it with {@link DumSurvivorship#wholeRecord}.
     */
    protected DumSurvivorship survivorship() {
        return DumSurvivorship.fieldMerge();
    }

    /** A run of {@code source} against the resolution index, or {@code null} without a canonical key. */
    private DumResolutionRun resolutionRun(DumStructuredSource source) {
        return canonicalKey == null ? null
                : new DumResolutionRun(canonicalKey, resolutionIndex, source, survivorship());
    }

    /** {@code rec} as it publishes: its canonical document when resolution is on (D109). */
    private static DumStructuredRecord resolved(DumResolutionRun resolution, DumStructuredRecord rec) {
        return resolution == null ? rec : resolution.resolve(rec);
    }

    /**
     * D104: ids the source reports unchanged and still present stay alive without an extract, so
     * whichever finish follows does not deindex them. A resolved record stays alive under its
     * canonical id (D109).
     */
    private void keepUnchangedAlive(DumStructuredSource structuredSource, DumConnectorSession session,
            DumResolutionRun resolution) {
        Collection<String> unchanged = structuredSource.unchangedIds();
        if (resolution != null) {
            unchanged = resolution.documentIds(unchanged);
        }
        if (!unchanged.isEmpty()) {
            int kept = dumConnectorContext.keepAlive(session, unchanged);
            log.debug("Structured source '{}' kept {} unchanged id(s) alive",
                    structuredSource.getSourceName(), kept);
        }
    }

    /**
     * A run that saw the whole source (D109): hold the members it no longer has, and keep publishing
     * the canonical documents another source still has a live member of, so this source's sweep does
     * not delete them.
     */
    private void publishHeld(DumStructuredSource structuredSource, DumConnectorSession session,
            DumResolutionRun resolution, DumPreviewSnapshot.Builder snapshot,
            DumProvenanceSnapshot.Builder provenance, DumContentTypeCoverageRecorder byType) {
        if (resolution != null) {
            resolution.closeComplete().forEach(held -> publish(structuredSource, held, session,
                    snapshot, provenance, byType));
        }
    }

    @Override
    public void indexAll(String source) {
        provider().resolve(source).ifPresentOrElse(structuredSource -> {
            DumRunEnd end = run(structuredSource, source);
            // D115: the source hears how the run ended once it has finished, on every path.
            try {
                structuredSource.onRunEnd(end);
            } catch (RuntimeException e) {
                log.warn("Structured source '{}' failed to end its run: {}", source, e.getMessage());
            }
        }, () -> log.error("Structured source '{}' not found", source));
    }

    /** One full run of {@code structuredSource}, finished on whichever path it takes. */
    private DumRunEnd run(DumStructuredSource structuredSource, String source) {
        // Declare the schema before the first document (D63): the backend creates the fields as
        // declared instead of inferring each one from whichever value arrives first.
        provisionSchema(structuredSource);
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
        // Cross-source resolution (D109): each keyed record publishes as its canonical document.
        DumResolutionRun resolution = resolutionRun(structuredSource);
        // Bounded-concurrency extraction (roadmap D31): extract(id) may run on a small pool while
        // the consumer below — publish + snapshot/coverage/provenance capture — stays serial on
        // this thread, so capture remains single-threaded and order-independent. Default
        // extractionConcurrency() == 1 keeps every adapter sequential.
        DumExtractionOutcome outcome;
        try {
            outcome = DumBoundedExtraction.extractEach(structuredSource, structuredSource.discover(),
                    extractionConcurrency(), extractionTimeoutMs(),
                    rec -> publish(structuredSource,
                            resolved(resolution, enrichmentChain.apply(structuredSource, rec)),
                            session, snapshot, provenance, byType));
        } catch (RuntimeException e) {
            // An exception escaped the extraction loop — finish standalone (D33) so the
            // destructive deindex sweep is skipped and the prior index survives.
            finishPartialRunStandalone(session, source, "aborted by an exception", e,
                    runNotes(structuredSource));
            return DumRunEnd.stopped("aborted by an exception");
        }
        // D104: ids the source reports unchanged and still present stay alive without an
        // extract, so whichever finish follows does not deindex them.
        keepUnchangedAlive(structuredSource, session, resolution);
        // D114: what the source found gone leaves now, whichever way the run ends below.
        int gone = deindexNow(structuredSource, session, resolution, structuredSource.goneIds());
        if (gone > 0) {
            log.info("Structured source '{}' deindexed {} id(s) it found gone", source, gone);
        }
        if (!outcome.completed()) {
            // The run was interrupted before visiting every discovered id; finishing
            // non-standalone would treat the rest of the catalog as removed (D33).
            String interrupted = "interrupted after %d of %d ids".formatted(outcome.processed(),
                    outcome.discovered());
            finishPartialRunStandalone(session, source, interrupted, null, runNotes(structuredSource));
            return DumRunEnd.stopped(interrupted);
        }
        if (structuredSource.incremental()) {
            return finishIncrementalRun(structuredSource, session, source, outcome, resolution);
        }
        if (!structuredSource.discoveryComplete()) {
            // Discovery itself stopped short (a call budget, a page cap, a failed page), so the
            // ids it never listed are not removals; finishing non-standalone would deindex them.
            finishPartialRunStandalone(session, source, DISCOVERY_STOPPED, null, runNotes(structuredSource));
            return DumRunEnd.stopped(DISCOVERY_STOPPED);
        }
        if (isFailureWave(outcome)) {
            // A wave of extraction failures (e.g. a network blip) would otherwise deindex every id
            // it could not re-fetch; finish standalone so the prior index survives (D34).
            finishPartialRunStandalone(session, source, failureWaveReason(outcome), null,
                    runNotes(structuredSource));
            return DumRunEnd.stopped(failureWaveReason(outcome));
        }
        publishHeld(structuredSource, session, resolution, snapshot, provenance, byType);
        // A complete run can still have something to say (D96); an empty note list records nothing.
        List<String> notes = runNotes(structuredSource);
        if (!notes.isEmpty()) {
            dumConnectorContext.recordRunOutcome(session, false, notes);
        }
        dumConnectorContext.finishIndexing(session, false);
        DumPreviewSnapshot baseline = snapshot.build();
        previewSnapshotStore.save(source, baseline);
        // Per-field coverage of this run, appended to the source's history so a later run can be
        // checked for drift (D15) — derived from the same fingerprint, no extra pass over the source.
        coverageHistoryStore.recordSnapshot(source, baseline.documents().size(),
                DumCoverageMonitor.coverage(structuredSource.schema(), baseline));
        provenanceHistoryStore.recordSnapshot(source, provenance.documentCount(), provenance.fields());
        byType.flush(source, coverageHistoryStore, provenanceHistoryStore);
        return DumRunEnd.complete();
    }

    /**
     * Declares the source's schema to the indexing backend, once per site it publishes to, before
     * any document of this run (roadmap <strong>D63</strong>).
     *
     * <p>
     * The fields are the same {@code TurSNJobAttributeSpec}s attached to every published document,
     * so there is no second schema to keep in step. Called from {@link #indexAll(String)} only: that
     * is the run that establishes a source in the index, and the declaration is what the
     * partial-run paths ({@link #finishPartialRunStandalone}) are protecting. A per-id
     * {@link #indexById} call deliberately does not provision — it is a single-document update
     * against a site a full run already declared, and a schema change belongs to a full run.
     *
     * <p>
     * A source publishing to no site (no site names configured) has nothing to declare.
     */
    private void provisionSchema(DumStructuredSource structuredSource) {
        List<TurSNJobAttributeSpec> fields = structuredSource.schema().toAttributeSpecs();
        for (String siteName : structuredSource.getSiteNames()) {
            dumConnectorContext.provisionSchema(
                    new DumIndexingSchema(siteName, structuredSource.getLocale(), fields));
        }
    }

    /**
     * Finish a partial / interrupted {@code indexAll} run safely (roadmap <strong>D33</strong>):
     * close the session <em>standalone</em> so {@link DumConnectorContext#finishIndexing} skips the
     * tracking-driven deindex sweep, and the previously indexed documents survive instead of every
     * not-yet-extracted id being treated as removed and wiped. Generalises D09's zero-yield safe-run
     * to the partial / aborted structured path. The run-end baseline/coverage/provenance capture is
     * deliberately not performed by the caller — a partial run must never overwrite the last full
     * index's baseline.
     *
     * @param cause the exception that aborted the run, or {@code null} for a clean interrupt
     */
    /**
     * The notes a run records (D96): the source's own, then each enricher's end-of-run summary
     * (D100). Called once per run, on whichever path ends it, since ending the enrichers' run
     * also resets their per-run state.
     */
    private List<String> runNotes(DumStructuredSource structuredSource) {
        List<String> notes = new ArrayList<>(structuredSource.runNotes());
        notes.addAll(enrichmentChain.runEnd(structuredSource));
        return notes;
    }

    private DumRunEnd finishIncrementalRun(DumStructuredSource structuredSource, DumConnectorSession session,
            String source, DumExtractionOutcome outcome, DumResolutionRun resolution) {
        // An incremental source (D92) never sweeps: no run is expected to reach every item, so what a
        // run missed is not a removal. Items leave by expiry instead — unless this run was a failure
        // wave, which says nothing about what is gone.
        if (isFailureWave(outcome)) {
            finishPartialRunStandalone(session, source, failureWaveReason(outcome), null,
                    runNotes(structuredSource));
            return DumRunEnd.stopped(failureWaveReason(outcome));
        }
        List<String> notes = new ArrayList<>(runNotes(structuredSource));
        int queued = deindexNow(structuredSource, session, resolution, structuredSource.expiredIds());
        if (queued > 0) {
            log.info("Incremental source '{}' deindexed {} expired id(s)", source, queued);
            notes.add("%d expired record(s) deindexed".formatted(queued));
        }
        // An incremental run that stopped early is partial, but it is not a failure (D96).
        boolean partial = !structuredSource.discoveryComplete();
        dumConnectorContext.recordRunOutcome(session, partial, notes);
        dumConnectorContext.finishIndexing(session, true);
        return new DumRunEnd(partial, false, partial ? DISCOVERY_STOPPED : null);
    }

    /**
     * Deindex {@code ids} under {@code session} now: an incremental run's expired ids (D92) or what a
     * source found gone (D114). Under resolution a member leaves its canonical document only once no
     * member of it is live (D109); until then the document is published again without it.
     *
     * @return how many delete jobs were queued
     */
    private int deindexNow(DumStructuredSource structuredSource, DumConnectorSession session,
            DumResolutionRun resolution, Collection<String> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        Collection<String> deindex = ids;
        if (resolution != null) {
            DumResolutionRun.Expiry expiry = resolution.expire(ids);
            expiry.republish().forEach(kept -> publish(structuredSource, kept, session, null, null, null));
            deindex = expiry.deindex();
        }
        return deindex.isEmpty() ? 0
                : dumConnectorContext.deIndexById(session, deindex, List.copyOf(structuredSource.getSiteNames()));
    }

    protected void finishPartialRunStandalone(DumConnectorSession session, String source,
            String reason, RuntimeException cause) {
        finishPartialRunStandalone(session, source, reason, cause, List.of());
    }

    /**
     * {@link #finishPartialRunStandalone(DumConnectorSession, String, String, RuntimeException)},
     * with the source's own notes recorded after the reason (roadmap D96), so the run record says
     * both that the run stopped and what the source knows about why.
     *
     * <p>
     * The cause is logged before the finish path runs and recorded as the note after the reason
     * (D126): when finishing itself fails, that failure is logged on its own and carries the cause
     * as suppressed, so it never hides what stopped the run.
     */
    protected void finishPartialRunStandalone(DumConnectorSession session, String source,
            String reason, RuntimeException cause, List<String> sourceNotes) {
        List<String> notes = new ArrayList<>();
        notes.add(reason);
        if (cause != null) {
            log.error("Structured indexAll for source '{}' did not complete ({}) — finishing standalone, "
                    + "previously indexed documents preserved (no deindex)", source, reason, cause);
            notes.add(causeNote(cause));
        } else {
            log.warn("Structured indexAll for source '{}' did not complete ({}) — finishing standalone, "
                    + "previously indexed documents preserved (no deindex)", source, reason);
        }
        notes.addAll(sourceNotes);
        try {
            dumConnectorContext.recordRunOutcome(session, true, notes);
            dumConnectorContext.finishIndexing(session, true);
        } catch (RuntimeException finishFailure) {
            log.error("Finishing the partial run of source '{}' failed as well", source, finishFailure);
            if (cause != null) {
                finishFailure.addSuppressed(cause);
            }
            throw finishFailure;
        }
    }

    /** The run-record note naming what aborted a run: its class and message (D126). */
    static String causeNote(Throwable cause) {
        String message = cause.getMessage();
        return "cause: " + cause.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    @Override
    public void indexById(String source, List<String> contentId) {
        provider().resolve(source).ifPresentOrElse(structuredSource -> {
            DumConnectorSession session = newSession(structuredSource);
            DumResolutionRun resolution = resolutionRun(structuredSource);
            contentId.forEach(id -> structuredSource.extract(id).ifPresentOrElse(
                    rec -> publish(structuredSource,
                            resolved(resolution, enrichmentChain.apply(structuredSource, rec)), session,
                            null, null, null),
                    () -> log.warn("No record extracted for id '{}' in source '{}'", id, source)));
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

    private void publish(DumStructuredSource source, DumStructuredRecord rec,
            DumConnectorSession session, DumPreviewSnapshot.Builder snapshot,
            DumProvenanceSnapshot.Builder provenance, DumContentTypeCoverageRecorder byType) {
        List<String> missing = rec.missingMandatoryFields();
        if (!missing.isEmpty()) {
            log.warn("Skipping record '{}' in source '{}' — missing mandatory field(s): {}",
                    rec.id(), source.getSourceName(), missing);
            return;
        }
        TurSNJobItem item = toJobItem(source, rec);
        // D127: the reindex strategy compares checksums, so an item must carry one. A plugin that
        // stamps its own (AEM's delta date) keeps it; otherwise it is the hash of what is published,
        // taken after toJobItem so a subclass's added attributes count too.
        if (item.getChecksum() == null) {
            item.setChecksum(DumJobItemChecksum.of(item));
        }
        dumConnectorContext.addJobItem(new DumJobItemWithSession(item, session, Set.of(), false));
        if (snapshot != null) {
            snapshot.add(rec.id(), rec.values());
        }
        if (provenance != null) {
            provenance.add(rec.values(), rec.provenance());
        }
        if (byType != null) {
            byType.add(rec);
        }
    }

    /**
     * Build the {@link TurSNJobItem} for a record: grounded attributes (plus the record id under the
     * standard {@code id} field) and the declared schema specs. When the record carries field-level
     * provenance (roadmap D16), a compact reserved {@code dum_provenance} attribute + spec are added
     * so the search/RAG side can cite and audit where each attribute came from. Subclasses may
     * override to add provider-specific attributes (e.g. {@code source_apps}).
     */
    protected TurSNJobItem toJobItem(DumStructuredSource source, DumStructuredRecord rec) {
        Map<String, Object> attributes = new HashMap<>(rec.values());
        attributes.putIfAbsent(TurSNFieldName.ID, rec.id());
        projectEmbeddingFields(source.schema(), attributes);
        List<TurSNJobAttributeSpec> specs = new ArrayList<>(source.schema().toAttributeSpecs());
        DumProvenance.contribute(source.getSourceName(), rec, attributes, specs);
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

    /**
     * A session for one run of {@code source}. Each document the connector deindexes under it is
     * reported to the source and to the enrichers (roadmap <strong>D113</strong>).
     */
    private DumConnectorSession newSession(DumStructuredSource source) {
        return new DumConnectorSession(source.getSourceName(), source.getProviderName(),
                source.getLocale()).onDeindex((documentId, sites, locale) -> {
                    try {
                        source.deindexed(documentId);
                    } catch (RuntimeException e) {
                        log.warn("Structured source '{}' failed on deindexed '{}': {}",
                                source.getSourceName(), documentId, e.getMessage());
                    }
                    enrichmentChain.deindexed(source, documentId);
                });
    }
}
