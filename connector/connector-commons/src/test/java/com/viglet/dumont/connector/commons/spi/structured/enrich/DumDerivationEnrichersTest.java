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

package com.viglet.dumont.connector.commons.spi.structured.enrich;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * The reusable derivation enrichers (roadmap D26) — each reproduces one business derivation the AEM
 * god-class fused into extraction, now grounding-safe and individually testable.
 */
class DumDerivationEnrichersTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    // A fixed clock so "today" is deterministic: 2024-06-01 in São Paulo.
    private static final java.util.function.Supplier<Instant> CLOCK =
            () -> Instant.parse("2024-06-01T12:00:00Z");
    private static final Date FAR_FUTURE =
            Date.from(LocalDate.of(2100, 1, 1).atStartOfDay(SAO_PAULO).toInstant());

    private final DumStructuredSchema schema = DumStructuredSchema.builder()
            .field(DumStructuredField.text("title"))
            .field(DumStructuredField.facet("alphabet"))
            .field(date("startOfClasses", true))
            .field(date("endOfClasses", true))
            .field(date("dateSort", false))
            .field(DumStructuredField.builder("dateOfClassesText").type(TurSEFieldType.TEXT)
                    .multiValued(true).build())
            .field(bool("openContest", true))
            .field(bool("openOffer", true))
            .field(bool("inscricaoAberta", false))
            .field(money("precoContest"))
            .field(money("precoOffer"))
            .field(money("preco"))
            .build();

    private final DumStructuredSource source = new StubSource(schema);

    @Test
    void distinctValuesAggregatesAndDedupsPreservingOrder() {
        DumStructuredRecord rec = DumStructuredRecord.builder("r1", schema)
                .set("precoContest", List.of(100.0, 200.0))
                .set("precoOffer", List.of(200.0, 300.0))
                .build();
        DumDistinctValuesEnricher preco = new DumDistinctValuesEnricher("preco", "preco",
                List.of("precoContest", "precoOffer"));
        assertEquals(List.of(100.0, 200.0, 300.0), preco.enrich(source, rec).get("preco"));
    }

    @Test
    void distinctValuesContributesNothingWhenSourcesAbsent() {
        DumStructuredRecord empty = DumStructuredRecord.builder("r1", schema).set("title", "x").build();
        assertTrue(new DumDistinctValuesEnricher("preco", "preco", List.of("precoContest"))
                .enrich(source, empty).isEmpty());
    }

    @Test
    void booleanAnyIsTrueWhenAnySourceFlagIsTrue() {
        DumStructuredRecord rec = DumStructuredRecord.builder("r1", schema)
                .set("openContest", List.of(false))
                .set("openOffer", List.of(true))
                .build();
        DumBooleanAnyEnricher open = new DumBooleanAnyEnricher("inscricaoAberta", "inscricaoAberta",
                List.of("openContest", "openOffer"), Boolean.FALSE);
        assertEquals(Boolean.TRUE, open.enrich(source, rec).get("inscricaoAberta"));
    }

    @Test
    void booleanAnyIsFalseWhenAllPresentFlagsAreFalse() {
        DumStructuredRecord rec = DumStructuredRecord.builder("r1", schema)
                .set("openContest", List.of(false, false))
                .build();
        DumBooleanAnyEnricher open = new DumBooleanAnyEnricher("inscricaoAberta", "inscricaoAberta",
                List.of("openContest", "openOffer"), Boolean.FALSE);
        assertEquals(Boolean.FALSE, open.enrich(source, rec).get("inscricaoAberta"));
    }

    @Test
    void booleanAnyUsesDefaultWhenNoSourceFlagPresent() {
        DumStructuredRecord rec = DumStructuredRecord.builder("r1", schema).set("title", "x").build();
        DumBooleanAnyEnricher withDefault = new DumBooleanAnyEnricher("inscricaoAberta",
                "inscricaoAberta", List.of("openContest"), Boolean.FALSE);
        assertEquals(Boolean.FALSE, withDefault.enrich(source, rec).get("inscricaoAberta"));

        DumBooleanAnyEnricher strict = new DumBooleanAnyEnricher("inscricaoAberta", "inscricaoAberta",
                List.of("openContest"), null);
        assertTrue(strict.enrich(source, rec).isEmpty(),
                "a null default leaves the flag absent (strict grounding)");
    }

    @Test
    void earliestDatePicksTheSoonestNonPastDateAcrossSources() {
        DumStructuredRecord rec = DumStructuredRecord.builder("r1", schema)
                .set("startOfClasses",
                        List.of(dateAt(2024, 1, 15), dateAt(2024, 9, 1), dateAt(2024, 12, 1)))
                .build();
        DumEarliestDateEnricher dateSort = new DumEarliestDateEnricher("dateSort", "dateSort",
                List.of("startOfClasses"), CLOCK, SAO_PAULO, FAR_FUTURE);
        assertEquals(dateAt(2024, 9, 1), dateSort.enrich(source, rec).get("dateSort"),
                "the 2024-01-15 date is in the past and is excluded");
    }

    @Test
    void earliestDateFallsBackToSentinelWhenNoFutureDate() {
        DumStructuredRecord rec = DumStructuredRecord.builder("r1", schema)
                .set("startOfClasses", List.of(dateAt(2020, 1, 1)))
                .build();
        DumEarliestDateEnricher dateSort = new DumEarliestDateEnricher("dateSort", "dateSort",
                List.of("startOfClasses"), CLOCK, SAO_PAULO, FAR_FUTURE);
        assertEquals(FAR_FUTURE, dateSort.enrich(source, rec).get("dateSort"));
    }

    @Test
    void dateRangeTextPairsByIndexSortsChronologicallyAndFormatsInPt() {
        DumStructuredRecord rec = DumStructuredRecord.builder("r1", schema)
                .set("startOfClasses", List.of(dateAt(2024, 9, 1), dateAt(2024, 1, 15)))
                .set("endOfClasses", List.of(dateAt(2024, 12, 1), dateAt(2024, 2, 20)))
                .build();
        DumDateRangeTextEnricher text = new DumDateRangeTextEnricher("dateOfClassesText",
                "dateOfClassesText", "startOfClasses", "endOfClasses", SAO_PAULO);
        assertEquals(List.of("15 Jan - 20 Fev 2024", "01 Set - 01 Dez 2024"),
                text.enrich(source, rec).get("dateOfClassesText"));
    }

    @Test
    void dateRangeTextContributesNothingWithoutCompletePairs() {
        DumStructuredRecord rec = DumStructuredRecord.builder("r1", schema)
                .set("startOfClasses", List.of(dateAt(2024, 9, 1)))
                .build();
        DumDateRangeTextEnricher text = new DumDateRangeTextEnricher("dateOfClassesText",
                "dateOfClassesText", "startOfClasses", "endOfClasses", SAO_PAULO);
        assertTrue(text.enrich(source, rec).isEmpty());
    }

    /**
     * The reference migration in miniature: a record carrying <em>only sourced</em> fields runs the
     * full derivation chain, and every derived field — {@code inscricaoAberta}, {@code preco},
     * {@code dateSort}, {@code dateOfClassesText}, {@code alphabet} — appears, each produced by a
     * named enricher (its provenance strategy is {@code enrichment:<name>}). This is the D26 "done"
     * shape: extraction emits sourced data, enrichers own every derivation.
     */
    @Test
    void theFullChainDerivesEveryFieldFromSourcedDataAndTagsEachEnricher() {
        DumStructuredRecord sourced = DumStructuredRecord.builder("r1", schema)
                .set("title", "José Silva")
                .set("openContest", List.of(false))
                .set("openOffer", List.of(true))
                .set("precoContest", List.of(100.0, 200.0))
                .set("precoOffer", List.of(200.0))
                .set("startOfClasses", List.of(dateAt(2024, 9, 1), dateAt(2024, 1, 15)))
                .set("endOfClasses", List.of(dateAt(2024, 12, 1), dateAt(2024, 2, 20)))
                .build();
        // No derived field is present coming out of "extraction".
        assertFalse(sourced.has("inscricaoAberta"));
        assertFalse(sourced.has("alphabet"));

        DumEnrichmentChain chain = DumEnrichmentChain.of(
                new DumBooleanAnyEnricher("inscricaoAberta", "inscricaoAberta",
                        List.of("openContest", "openOffer"), Boolean.FALSE),
                new DumDistinctValuesEnricher("preco", "preco",
                        List.of("precoContest", "precoOffer")),
                new DumEarliestDateEnricher("dateSort", "dateSort", List.of("startOfClasses"),
                        CLOCK, SAO_PAULO, FAR_FUTURE),
                new DumDateRangeTextEnricher("dateOfClassesText", "dateOfClassesText",
                        "startOfClasses", "endOfClasses", SAO_PAULO),
                DumNormalizationEnricher.transform("alphabet", "title", "alphabet",
                        com.viglet.dumont.connector.commons.spi.structured.normalize.DumTextNormalizers::firstLetter));

        DumStructuredRecord out = chain.apply(source, sourced);

        assertEquals(Boolean.TRUE, out.values().get("inscricaoAberta"));
        assertEquals(List.of(100.0, 200.0), out.values().get("preco"));
        assertEquals(dateAt(2024, 9, 1), out.values().get("dateSort"));
        assertEquals(List.of("15 Jan - 20 Fev 2024", "01 Set - 01 Dez 2024"),
                out.values().get("dateOfClassesText"));
        assertEquals(List.of("J"), out.values().get("alphabet"));

        assertEquals("enrichment:inscricaoAberta", out.provenance().get("inscricaoAberta").strategy());
        assertEquals("enrichment:preco", out.provenance().get("preco").strategy());
        assertEquals("enrichment:dateSort", out.provenance().get("dateSort").strategy());
        assertEquals("enrichment:dateOfClassesText",
                out.provenance().get("dateOfClassesText").strategy());
        assertEquals("enrichment:alphabet", out.provenance().get("alphabet").strategy());
        // Sourced fields keep their own (non-enrichment) provenance untouched.
        assertEquals("José Silva", out.values().get("title"));
    }

    private static Date dateAt(int year, int month, int day) {
        return Date.from(LocalDate.of(year, month, day).atStartOfDay(SAO_PAULO).toInstant());
    }

    private static DumStructuredField date(String name, boolean multiValued) {
        return DumStructuredField.builder(name).type(TurSEFieldType.DATE).multiValued(multiValued)
                .build();
    }

    private static DumStructuredField bool(String name, boolean multiValued) {
        return DumStructuredField.builder(name).type(TurSEFieldType.BOOL).multiValued(multiValued)
                .build();
    }

    private static DumStructuredField money(String name) {
        return DumStructuredField.builder(name).type(TurSEFieldType.DOUBLE).multiValued(true).build();
    }

    private record StubSource(DumStructuredSchema schema) implements DumStructuredSource {
        @Override
        public String getSourceName() {
            return "stub";
        }

        @Override
        public String getProviderName() {
            return "STUB";
        }

        @Override
        public Collection<String> getSiteNames() {
            return List.of("Sample");
        }

        @Override
        public List<String> discover() {
            return List.of();
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            return Optional.empty();
        }

        @Override
        public Locale getLocale() {
            return Locale.US;
        }
    }
}
