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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnrichmentChain;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumPreviewSnapshot.DumPreviewDoc;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumPreviewSnapshot.DumPreviewFieldSpec;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumStructuredPreview.Coverage;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumStructuredPreview.DocChange;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumStructuredPreview.DocumentDiff;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumStructuredPreview.FieldChange;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumStructuredPreview.SampleRecord;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumStructuredPreview.SchemaDiff;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumStructuredPreview.SkippedRecord;

/**
 * The dry-run preview engine (roadmap <strong>D14</strong>): given a {@link DumStructuredSource}, a
 * sample bound and the last indexed {@link DumPreviewSnapshot}, it extracts a bounded sample (through
 * the same {@code discover → extract → enrich} path the real index uses), validates it against the
 * declared schema, and diffs it against the baseline — <strong>writing nothing</strong>.
 *
 * <p>
 * It is a pure function of its inputs: it never publishes, never mutates the source, and never saves
 * a snapshot. Records that would be skipped at index time (missing a mandatory field) are reported
 * but excluded from the document diff, so the add / change / remove counts reflect what would truly
 * reach the index.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public final class DumStructuredPreviewer {

    /** Default sample bound when a caller does not request one. */
    public static final int DEFAULT_SAMPLE_SIZE = 20;

    /** Cap on removed-document ids listed in the diff; the {@code removed} count stays exact. */
    private static final int MAX_LISTED_REMOVED = 100;

    private DumStructuredPreviewer() {
    }

    /**
     * Run a preview over {@code source}, sampling at most {@code sampleSize} discovered ids and
     * diffing the result against {@code baseline} (use {@link DumPreviewSnapshot#empty()} for first
     * onboarding).
     */
    public static DumStructuredPreview preview(DumStructuredSource source, int sampleSize,
            DumEnrichmentChain enrichmentChain, DumPreviewSnapshot baseline) {
        DumEnrichmentChain enrich = enrichmentChain == null ? DumEnrichmentChain.empty()
                : enrichmentChain;
        DumPreviewSnapshot base = baseline == null ? DumPreviewSnapshot.empty() : baseline;
        DumStructuredSchema schema = source.schema();

        List<String> allIds = source.discover();
        int effectiveN = sampleSize <= 0 ? DEFAULT_SAMPLE_SIZE : sampleSize;

        List<DumStructuredRecord> records = allIds.stream()
                .limit(effectiveN)
                .map(id -> source.extract(id).map(record -> enrich.apply(source, record)).orElse(null))
                .filter(Objects::nonNull)
                .toList();

        List<Coverage> coverage = coverage(schema, records);

        List<SampleRecord> samples = new ArrayList<>();
        List<SkippedRecord> skipped = new ArrayList<>();
        Map<String, DumPreviewDoc> currentDocs = new LinkedHashMap<>();
        for (DumStructuredRecord record : records) {
            List<String> missing = record.missingMandatoryFields();
            boolean wouldIndex = missing.isEmpty();
            samples.add(new SampleRecord(record.id(), new LinkedHashMap<>(record.values()),
                    wouldIndex, missing));
            if (wouldIndex) {
                currentDocs.put(record.id(), DumPreviewDoc.of(record.values()));
            } else {
                skipped.add(new SkippedRecord(record.id(), missing));
            }
        }

        DocumentDiff documentDiff = diffDocuments(currentDocs, base, new HashSet<>(allIds));
        SchemaDiff schemaDiff = diffSchema(schema, base);

        return new DumStructuredPreview(source.getSourceName(), allIds.size(), records.size(),
                effectiveN, coverage, skipped, schemaDiff, documentDiff, samples, !base.isEmpty());
    }

    private static List<Coverage> coverage(DumStructuredSchema schema,
            List<DumStructuredRecord> records) {
        int total = records.size();
        return schema.fields().stream().map(field -> {
            int present = (int) records.stream().filter(record -> record.has(field.name())).count();
            int percent = total == 0 ? 0 : Math.round(present * 100f / total);
            return new Coverage(field.name(), field.type().name(), field.mandatory(), present, total,
                    percent);
        }).toList();
    }

    private static DocumentDiff diffDocuments(Map<String, DumPreviewDoc> current,
            DumPreviewSnapshot base, Set<String> discoveredIds) {
        int added = 0;
        int changed = 0;
        int unchanged = 0;
        List<DocChange> changes = new ArrayList<>();
        for (Map.Entry<String, DumPreviewDoc> entry : current.entrySet()) {
            DumPreviewDoc baseDoc = base.documents().get(entry.getKey());
            if (baseDoc == null) {
                added++;
                changes.add(new DocChange(entry.getKey(), "added", List.of()));
            } else if (baseDoc.digest().equals(entry.getValue().digest())) {
                unchanged++;
            } else {
                changed++;
                List<String> newlyEmpty = baseDoc.fields().stream()
                        .filter(field -> !entry.getValue().fields().contains(field))
                        .toList();
                changes.add(new DocChange(entry.getKey(), "changed", newlyEmpty));
            }
        }
        // Removed: baseline ids the source no longer discovers at all (a genuine deletion).
        List<String> removedIds = base.documents().keySet().stream()
                .filter(id -> !discoveredIds.contains(id))
                .toList();
        removedIds.stream().limit(MAX_LISTED_REMOVED)
                .forEach(id -> changes.add(new DocChange(id, "removed", List.of())));
        return new DocumentDiff(added, changed, removedIds.size(), unchanged, changes);
    }

    private static SchemaDiff diffSchema(DumStructuredSchema schema, DumPreviewSnapshot base) {
        if (base.schema().isEmpty()) {
            return new SchemaDiff(List.of(), List.of(), List.of());
        }
        Map<String, DumPreviewFieldSpec> baseFields = base.schema().stream()
                .collect(Collectors.toMap(DumPreviewFieldSpec::name, field -> field,
                        (first, second) -> first, LinkedHashMap::new));
        Map<String, DumStructuredField> currentFields = schema.fields().stream()
                .collect(Collectors.toMap(DumStructuredField::name, field -> field,
                        (first, second) -> first, LinkedHashMap::new));

        List<String> addedFields = currentFields.keySet().stream()
                .filter(name -> !baseFields.containsKey(name))
                .toList();
        List<String> removedFields = baseFields.keySet().stream()
                .filter(name -> !currentFields.containsKey(name))
                .toList();
        List<FieldChange> changedFields = new ArrayList<>();
        currentFields.forEach((name, field) -> {
            DumPreviewFieldSpec baseField = baseFields.get(name);
            if (baseField != null) {
                describeChange(baseField, field)
                        .ifPresent(change -> changedFields.add(new FieldChange(name, change)));
            }
        });
        return new SchemaDiff(addedFields, removedFields, changedFields);
    }

    private static java.util.Optional<String> describeChange(DumPreviewFieldSpec base,
            DumStructuredField current) {
        List<String> parts = new ArrayList<>();
        if (!base.type().equals(current.type().name())) {
            parts.add("type " + base.type() + "→" + current.type().name());
        }
        if (base.multiValued() != current.multiValued()) {
            parts.add("multiValued " + base.multiValued() + "→" + current.multiValued());
        }
        if (base.facet() != current.facet()) {
            parts.add("facet " + base.facet() + "→" + current.facet());
        }
        if (base.mandatory() != current.mandatory()) {
            parts.add("mandatory " + base.mandatory() + "→" + current.mandatory());
        }
        return parts.isEmpty() ? java.util.Optional.empty()
                : java.util.Optional.of(String.join(", ", parts));
    }
}
