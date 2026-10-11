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

package com.viglet.dumont.connector.plugin.jobs.source.dedupe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobFingerprintEntry;

/** Which duplicate is published (trabalharei TRA23). */
class DumJobPreferenceTest {

    private static DumJobFingerprintEntry member(String type, boolean salary, int description) {
        return DumJobFingerprintEntry.builder().sourceType(type).sourceName(type + "-br")
                .jobId(type + "-1").hasSalary(salary).descriptionLength(description).build();
    }

    private static List<String> order(DumJobFingerprintEntry... members) {
        List<DumJobFingerprintEntry> list = new ArrayList<>(List.of(members));
        Collections.reverse(list);
        return list.stream().sorted(DumJobPreference.ORDER).map(DumJobFingerprintEntry::getSourceType).toList();
    }

    @Test
    void aSalaryBeatsEverythingElse() {
        assertThat(order(member("jooble", false, 5000), member("jsearch", true, 10)))
                .containsExactly("jsearch", "jooble");
    }

    @Test
    void thenTheLongerDescription() {
        assertThat(order(member("jooble", true, 100), member("bne", true, 101)))
                .containsExactly("bne", "jooble");
    }

    @Test
    void betweenEqualsThePayPerClickPartnerThenAFixedOrder() {
        assertThat(order(member("jsearch", false, 50), member("bne", false, 50), member("adzuna", false, 50),
                member("jooble", false, 50), member("acme", false, 50)))
                .containsExactly("jooble", "adzuna", "bne", "jsearch", "acme");
    }

    @Test
    void twoSourcesOfOneTypeAreOrderedByNameThenPosting() {
        DumJobFingerprintEntry second = member("adzuna", false, 0).toBuilder().sourceName("adzuna-sp").build();
        DumJobFingerprintEntry first = member("adzuna", false, 0).toBuilder().sourceName("adzuna-br").build();
        assertThat(List.of(second, first).stream().sorted(DumJobPreference.ORDER).toList())
                .containsExactly(first, second);
    }
}
