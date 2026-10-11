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

package com.viglet.dumont.connector.commons.spi.structured.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance;

/** The survivorship rules a provider can declare (roadmap D110). */
class DumSurvivorshipTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .text("title").text("salary").text("description").text("applyUrl").build();

    /** adzuna, filed first: a short posting with no salary. jooble, filed second: a full one. */
    private final List<DumResolutionIndex.Member> members = List.of(
            member("adzuna", 0, rec("a-1", "Analyst", null, "Short.", "https://adzuna/1")),
            member("jooble", 1, rec("j-9", "Data Analyst", "R$ 5.000", "A much longer description.",
                    "https://jooble/9")));

    private static final Comparator<DumResolutionIndex.Member> SALARY_FIRST = Comparator
            .comparing((DumResolutionIndex.Member m) -> !m.rec().has("salary"));

    @Test
    void fieldMergeKeepsTheFirstFiledAndFillsGaps() {
        DumStructuredRecord document = DumSurvivorship.fieldMerge().survive("job-x", members);

        assertEquals("job-x", document.id());
        assertEquals("Analyst", document.values().get("title"));
        assertEquals("R$ 5.000", document.values().get("salary"), "the gap is filled by the later member");
        assertEquals("jooble", document.provenance().get("salary").source());
    }

    @Test
    void wholeRecordPublishesOneMemberAndMixesNothingIn() {
        DumStructuredRecord document = DumSurvivorship.wholeRecord(SALARY_FIRST).survive("job-x", members);

        assertEquals("job-x", document.id());
        assertEquals(Map.of("title", "Data Analyst", "salary", "R$ 5.000",
                "description", "A much longer description.", "applyUrl", "https://jooble/9"), document.values());
        assertEquals("jooble", document.provenance().get("applyUrl").source());
    }

    @Test
    void wholeRecordBreaksTiesByFirstFiledOrder() {
        Comparator<DumResolutionIndex.Member> indifferent = Comparator.comparing(m -> 0);

        DumStructuredRecord document = DumSurvivorship.wholeRecord(indifferent).survive("job-x", members);

        assertEquals("Analyst", document.values().get("title"));
        assertFalse(document.has("salary"), "the winner is published as it is, gaps included");
    }

    @Test
    void fieldMergeWithRulesPicksEachFieldByItsOwnOrder() {
        Comparator<DumResolutionIndex.Member> longestDescription = Comparator.comparingInt(
                (DumResolutionIndex.Member m) -> -String.valueOf(m.rec().values().get("description")).length());

        DumStructuredRecord document = DumSurvivorship.fieldMerge(Map.of("description", longestDescription))
                .survive("job-x", members);

        assertEquals("A much longer description.", document.values().get("description"));
        assertEquals("jooble", document.provenance().get("description").source());
        assertEquals("Analyst", document.values().get("title"), "a field with no rule follows the first filed");
        assertEquals("adzuna", document.provenance().get("title").source());
        assertEquals("https://adzuna/1", document.values().get("applyUrl"));
    }

    private static DumResolutionIndex.Member member(String source, long order, DumStructuredRecord rec) {
        return new DumResolutionIndex.Member("job-x", source, rec.id(), rec, true, order);
    }

    private static DumStructuredRecord rec(String id, String title, String salary, String description,
            String applyUrl) {
        return DumStructuredRecord.builder(id, SCHEMA)
                .set("title", title, DumFieldProvenance.css("h1"))
                .set("salary", salary, DumFieldProvenance.css(".salary"))
                .set("description", description, DumFieldProvenance.css(".text"))
                .set("applyUrl", applyUrl, DumFieldProvenance.css("a.apply"))
                .build();
    }
}
