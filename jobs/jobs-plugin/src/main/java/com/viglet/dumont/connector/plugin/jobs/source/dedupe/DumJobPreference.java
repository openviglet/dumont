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

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobFingerprintEntry;

/**
 * Which member of a duplicate group is published (trabalharei <strong>TRA23</strong>). The order,
 * best first:
 *
 * <ol>
 * <li>the richest record: a salary present before none, then the longer description;</li>
 * <li>between equals, the partner that pays per click &mdash; Jooble;</li>
 * <li>then a fixed order of the others: Adzuna, BNE, JSearch, then any other type alphabetically;</li>
 * <li>then, for two members of the same type, the source name and the posting id, so the choice never
 * depends on which run wrote its row first.</li>
 * </ol>
 *
 * <p>
 * The whole record wins, not field by field: the published document is one partner's posting, so
 * its apply link, salary and description agree with each other.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public final class DumJobPreference {

    private DumJobPreference() {
    }

    /** Source types in preference order between equally rich members (pay-per-click first). */
    public static final List<String> SOURCE_ORDER = List.of("jooble", "adzuna", "bne", "jsearch");

    /** Best member first. */
    public static final Comparator<DumJobFingerprintEntry> ORDER = Comparator
            .comparing(DumJobFingerprintEntry::isHasSalary, Comparator.reverseOrder())
            .thenComparing(DumJobFingerprintEntry::getDescriptionLength, Comparator.reverseOrder())
            .thenComparingInt(entry -> rank(entry.getSourceType()))
            .thenComparing(entry -> type(entry.getSourceType()))
            .thenComparing(entry -> nullToEmpty(entry.getSourceName()))
            .thenComparing(entry -> nullToEmpty(entry.getJobId()));

    /** The position of {@code sourceType} in {@link #SOURCE_ORDER}; unknown types rank after. */
    static int rank(String sourceType) {
        int index = SOURCE_ORDER.indexOf(type(sourceType));
        return index < 0 ? SOURCE_ORDER.size() : index;
    }

    private static String type(String sourceType) {
        return sourceType == null ? "" : sourceType.trim().toLowerCase(Locale.ROOT);
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
