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

package com.viglet.dumont.connector.commons.spi.structured.normalize;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

/**
 * Stateless, timezone-aware date normalizers shared across structured sources (roadmap
 * <strong>D26</strong>).
 *
 * <p>
 * The cross-cutting date concerns the AEM god-class fused into extraction — "today" in a given zone
 * (for filtering future dates), a human date-range label in Portuguese month abbreviations — become
 * pure functions here. No method reads the system clock: the reference {@link Instant} is always an
 * argument, so behaviour is deterministic and unit-testable.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumDateNormalizers {

    /** Portuguese month abbreviations, index 0 = January, matching {@link ZonedDateTime#getMonthValue()} - 1. */
    private static final String[] MONTHS_PT = {
            "Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
            "Jul", "Ago", "Set", "Out", "Nov", "Dez"};

    private DumDateNormalizers() {
        throw new IllegalStateException("Utility class");
    }

    /** The Portuguese abbreviation for a 1-based month (1 = Jan ... 12 = Dez). */
    public static String monthAbbrevPt(int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month out of range (1-12): " + month);
        }
        return MONTHS_PT[month - 1];
    }

    /**
     * The start of the calendar day that {@code reference} falls on in {@code zone} — midnight,
     * local to the zone, as an {@link Instant}. The boundary used to keep "today and later" dates and
     * drop past ones regardless of the server's own timezone.
     */
    public static Instant startOfDay(Instant reference, ZoneId zone) {
        return reference.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
    }

    /**
     * A human label for a class/event date range in {@code zone}, e.g. {@code "15 Jan - 20 Fev 2024"}
     * (day-of-month, PT month abbreviation; the year shown once, from the end date). Returns
     * {@link Optional#empty()} if either bound is {@code null}, so an incomplete range yields no
     * value rather than a half-formed one.
     */
    public static Optional<String> dateRangeText(Date start, Date end, ZoneId zone) {
        if (start == null || end == null) {
            return Optional.empty();
        }
        ZonedDateTime from = start.toInstant().atZone(zone);
        ZonedDateTime to = end.toInstant().atZone(zone);
        return Optional.of("%02d %s - %02d %s %d".formatted(
                from.getDayOfMonth(), monthAbbrevPt(from.getMonthValue()),
                to.getDayOfMonth(), monthAbbrevPt(to.getMonthValue()), to.getYear()));
    }
}
