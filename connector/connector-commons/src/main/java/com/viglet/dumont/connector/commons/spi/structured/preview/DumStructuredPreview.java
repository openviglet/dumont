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

package com.viglet.dumont.connector.commons.spi.structured.preview;

import java.util.List;
import java.util.Map;

/**
 * The outcome of a dry-run preview (roadmap <strong>D14</strong>): what a real index run
 * <em>would</em> produce, computed without writing anything.
 *
 * <p>
 * It promotes the old "Dry Scan" (a count of what would be crawled) into a preview index — a bounded
 * sample is extracted, validated against the declared schema, and diffed against the last indexed
 * {@link DumPreviewSnapshot}. The report carries per-field {@link Coverage coverage}, the sampled
 * records that would be <em>skipped</em> for missing a mandatory field, a {@link SchemaDiff schema
 * diff} versus the baseline, and a {@link DocumentDiff document diff} (add / change / remove counts
 * plus newly-empty fields). De-risks both first onboarding (empty baseline → all added) and re-config
 * of a live source (diff versus what is indexed today).
 *
 * @param source       the previewed source name
 * @param discovered   total ids the source currently discovers
 * @param sampled      ids that were actually extracted into a record (≤ {@code sampleSize})
 * @param sampleSize   the requested sample bound
 * @param coverage     per declared field, how many of the sampled records carried a value
 * @param skipped      sampled records that would not index (missing a mandatory field), and why
 * @param schemaDiff   declared-schema changes versus the baseline (empty on first onboarding)
 * @param documentDiff add / change / remove versus the baseline, scoped to the sample
 * @param samples      the would-be documents (grounded attributes) for inspection
 * @param baseline     whether a prior indexed baseline existed (false ⇒ first onboarding)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public record DumStructuredPreview(String source, int discovered, int sampled, int sampleSize,
        List<Coverage> coverage, List<SkippedRecord> skipped, SchemaDiff schemaDiff,
        DocumentDiff documentDiff, List<SampleRecord> samples, boolean baseline) {

    /** Per-field coverage over the sampled records — {@code present} of {@code total} carried a value. */
    public record Coverage(String field, String type, boolean mandatory, int present, int total,
            int percent) {
    }

    /** A sampled record that would be skipped at index time, naming the mandatory field(s) it lacks. */
    public record SkippedRecord(String id, List<String> missingMandatoryFields) {
    }

    /** Declared-schema changes versus the baseline schema. */
    public record SchemaDiff(List<String> addedFields, List<String> removedFields,
            List<FieldChange> changedFields) {

        public boolean isEmpty() {
            return addedFields.isEmpty() && removedFields.isEmpty() && changedFields.isEmpty();
        }
    }

    /** One field whose declaration changed between baseline and current (e.g. {@code type STRING→TEXT}). */
    public record FieldChange(String field, String change) {
    }

    /**
     * Add / change / remove counts versus the baseline, scoped to the sample (plus baseline ids the
     * source no longer discovers, which count as removed). {@code changes} lists the per-document
     * detail; the removed list may be capped for very large baselines while {@code removed} stays
     * exact.
     */
    public record DocumentDiff(int added, int changed, int removed, int unchanged,
            List<DocChange> changes) {
    }

    /** One document's status in the diff; {@code newlyEmptyFields} is populated for a changed doc. */
    public record DocChange(String id, String status, List<String> newlyEmptyFields) {
    }

    /** A would-be document: its id, grounded attributes, and whether it would index. */
    public record SampleRecord(String id, Map<String, Object> attributes, boolean wouldIndex,
            List<String> missingMandatoryFields) {
    }
}
