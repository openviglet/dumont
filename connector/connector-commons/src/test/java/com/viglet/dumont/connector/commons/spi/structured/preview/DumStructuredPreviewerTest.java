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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnrichmentChain;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumPreviewSnapshot.DumPreviewFieldSpec;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumStructuredPreview.Coverage;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumStructuredPreview.DocChange;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * Unit test for the dry-run preview engine (roadmap D14): per-field coverage, mandatory-skip
 * reporting, and an add/change/remove diff (with newly-empty fields) against a baseline snapshot —
 * all computed without writing.
 *
 * <p>
 * The catalog has four rows: {@code c1} (full), {@code c2} and {@code c2b} (title only), and
 * {@code c3} (missing the mandatory title, so it would be skipped at index time).
 */
class DumStructuredPreviewerTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .field(DumStructuredField.builder("title").type(TurSEFieldType.TEXT).mandatory(true).build())
            .facet("area")
            .text("description")
            .field(DumStructuredField.builder("credits").type(TurSEFieldType.INT).build())
            .build();

    @Test
    void firstOnboardingReportsCoverageAndAllAdded() {
        DumStructuredPreview preview = DumStructuredPreviewer.preview(sampleSource(), 10,
                DumEnrichmentChain.empty(), DumPreviewSnapshot.empty());

        assertEquals(4, preview.discovered());
        assertEquals(4, preview.sampled());
        assertFalse(preview.baseline(), "no prior index → not a baseline run");

        // c3 lacks the mandatory title -> reported as skipped, never counted as an indexable doc.
        assertEquals(List.of("c3"), preview.skipped().stream().map(s -> s.id()).toList());

        // Coverage over the 4 extracted records.
        assertEquals(3, coverage(preview, "title").present()); // c1, c2, c2b
        assertEquals(75, coverage(preview, "title").percent());
        assertEquals(2, coverage(preview, "area").present()); // c1, c3
        assertEquals(1, coverage(preview, "description").present()); // only c1
        assertEquals(0, coverage(preview, "credits").present());

        // Three records would index; all are new; nothing changed/removed; schema diff neutral.
        assertEquals(3, preview.documentDiff().added());
        assertEquals(0, preview.documentDiff().changed());
        assertEquals(0, preview.documentDiff().removed());
        assertEquals(0, preview.documentDiff().unchanged());
        assertTrue(preview.schemaDiff().isEmpty());
    }

    @Test
    void diffDetectsAddedChangedRemovedAndNewlyEmptyFields() {
        // Baseline = a prior full index: c1 identical, c2 had a description it no longer declares,
        // and c4 is now gone. c2b is new.
        DumPreviewSnapshot baseline = DumPreviewSnapshot.builder(SCHEMA)
                .add("c1", Map.of("title", "Algorithms", "area", List.of("CS"), "description", "Sorting"))
                .add("c2", Map.of("title", "Calculus", "description", "Intro to limits"))
                .add("c4", Map.of("title", "Old course"))
                .build();

        DumStructuredPreview preview = DumStructuredPreviewer.preview(sampleSource(), 10,
                DumEnrichmentChain.empty(), baseline);

        assertTrue(preview.baseline(), "a prior index existed → baseline run");
        assertEquals(1, preview.documentDiff().added(), "c2b is new");
        assertEquals(1, preview.documentDiff().changed(), "c2 lost its description");
        assertEquals(1, preview.documentDiff().unchanged(), "c1 is identical");
        assertEquals(1, preview.documentDiff().removed(), "c4 is no longer discovered");

        DocChange changed = preview.documentDiff().changes().stream()
                .filter(c -> "changed".equals(c.status())).findFirst().orElseThrow();
        assertEquals("c2", changed.id());
        assertEquals(List.of("description"), changed.newlyEmptyFields());

        assertTrue(preview.documentDiff().changes().stream()
                .anyMatch(c -> "removed".equals(c.status()) && "c4".equals(c.id())));
    }

    @Test
    void respectsSampleSizeBound() {
        DumStructuredPreview preview = DumStructuredPreviewer.preview(sampleSource(), 1,
                DumEnrichmentChain.empty(), DumPreviewSnapshot.empty());

        assertEquals(4, preview.discovered());
        assertEquals(1, preview.sampled(), "only the first id was sampled");
        assertEquals(1, preview.samples().size());
    }

    @Test
    void schemaDiffNamesAddedRemovedAndChangedFields() {
        // Baseline schema: a removed "summary" field, "credits" was STRING, and no "area".
        DumPreviewSnapshot baseline = new DumPreviewSnapshot(List.of(
                new DumPreviewFieldSpec("title", "TEXT", false, false, true),
                new DumPreviewFieldSpec("summary", "TEXT", false, false, false),
                new DumPreviewFieldSpec("credits", "STRING", false, false, false),
                new DumPreviewFieldSpec("description", "TEXT", false, false, false)),
                Map.of());

        DumStructuredPreview preview = DumStructuredPreviewer.preview(sampleSource(), 10,
                DumEnrichmentChain.empty(), baseline);

        assertEquals(List.of("area"), preview.schemaDiff().addedFields());
        assertEquals(List.of("summary"), preview.schemaDiff().removedFields());
        assertEquals(1, preview.schemaDiff().changedFields().size());
        assertEquals("credits", preview.schemaDiff().changedFields().getFirst().field());
        assertTrue(preview.schemaDiff().changedFields().getFirst().change().contains("STRING→INT"));
    }

    private static Coverage coverage(DumStructuredPreview preview, String field) {
        return preview.coverage().stream().filter(c -> field.equals(c.field())).findFirst()
                .orElseThrow();
    }

    private static DumStructuredSource sampleSource() {
        Map<String, DumStructuredRecord> records = new LinkedHashMap<>();
        records.put("c1", DumStructuredRecord.builder("c1", SCHEMA)
                .set("title", "Algorithms").set("area", "CS").set("description", "Sorting").build());
        records.put("c2", DumStructuredRecord.builder("c2", SCHEMA)
                .set("title", "Calculus").build());
        records.put("c2b", DumStructuredRecord.builder("c2b", SCHEMA)
                .set("title", "Geometry").build());
        records.put("c3", DumStructuredRecord.builder("c3", SCHEMA)
                .set("area", "Physics").build());
        return new MapSource(records);
    }

    private record MapSource(Map<String, DumStructuredRecord> records) implements DumStructuredSource {
        @Override
        public String getSourceName() {
            return "courses";
        }

        @Override
        public String getProviderName() {
            return "STRUCTURED-TEST";
        }

        @Override
        public Collection<String> getSiteNames() {
            return List.of("Sample");
        }

        @Override
        public DumStructuredSchema schema() {
            return SCHEMA;
        }

        @Override
        public Locale getLocale() {
            return Locale.US;
        }

        @Override
        public List<String> discover() {
            return List.copyOf(records.keySet());
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            return Optional.ofNullable(records.get(contentId));
        }
    }
}
