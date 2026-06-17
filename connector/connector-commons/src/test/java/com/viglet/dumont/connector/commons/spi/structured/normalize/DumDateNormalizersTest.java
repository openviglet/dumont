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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Timezone-aware, deterministic behaviour of the shared date normalizers (roadmap D26). */
class DumDateNormalizersTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private static Date dateAt(int year, int month, int day) {
        return Date.from(LocalDate.of(year, month, day).atStartOfDay(SAO_PAULO).toInstant());
    }

    @Test
    void monthAbbrevPtIsPortuguese() {
        assertEquals("Jan", DumDateNormalizers.monthAbbrevPt(1));
        assertEquals("Fev", DumDateNormalizers.monthAbbrevPt(2));
        assertEquals("Dez", DumDateNormalizers.monthAbbrevPt(12));
    }

    @Test
    void monthAbbrevPtRejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> DumDateNormalizers.monthAbbrevPt(0));
        assertThrows(IllegalArgumentException.class, () -> DumDateNormalizers.monthAbbrevPt(13));
    }

    @Test
    void startOfDayIsLocalMidnightRegardlessOfReferenceInstant() {
        // 2024-03-10 02:00 UTC is still 2024-03-09 23:00 in São Paulo (UTC-3) -> day is the 9th.
        Instant reference = Instant.parse("2024-03-10T02:00:00Z");
        Instant expected = LocalDate.of(2024, 3, 9).atStartOfDay(SAO_PAULO).toInstant();
        assertEquals(expected, DumDateNormalizers.startOfDay(reference, SAO_PAULO));
    }

    @Test
    void dateRangeTextFormatsWithPtMonthsAndTrailingYear() {
        assertEquals(Optional.of("15 Jan - 20 Fev 2024"),
                DumDateNormalizers.dateRangeText(dateAt(2024, 1, 15), dateAt(2024, 2, 20), SAO_PAULO));
    }

    @Test
    void dateRangeTextYieldsNothingWhenABoundIsMissing() {
        assertTrue(DumDateNormalizers.dateRangeText(null, dateAt(2024, 2, 20), SAO_PAULO).isEmpty());
        assertTrue(DumDateNormalizers.dateRangeText(dateAt(2024, 1, 15), null, SAO_PAULO).isEmpty());
    }
}
