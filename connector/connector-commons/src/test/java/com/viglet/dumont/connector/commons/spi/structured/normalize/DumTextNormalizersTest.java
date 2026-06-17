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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Grounding-safe behaviour of the shared text normalizers (roadmap D26). */
class DumTextNormalizersTest {

    @Test
    void firstLetterFoldsAccentsAndUpperCases() {
        assertEquals(Optional.of("J"), DumTextNormalizers.firstLetter("José Silva"));
        assertEquals(Optional.of("A"), DumTextNormalizers.firstLetter("ávila"));
        assertEquals(Optional.of("B"), DumTextNormalizers.firstLetter("  bob"));
    }

    @Test
    void firstLetterYieldsNothingForBlankOrNonLetter() {
        assertTrue(DumTextNormalizers.firstLetter(null).isEmpty());
        assertTrue(DumTextNormalizers.firstLetter("   ").isEmpty());
        assertTrue(DumTextNormalizers.firstLetter("123").isEmpty(),
                "a non-letter leading char grounds away rather than indexing a digit");
    }

    @Test
    void htmlToTextStripsTagsAndDecodesEntities() {
        assertEquals(Optional.of("Hi there"),
                DumTextNormalizers.htmlToText("<p>Hi <b>there</b></p>"));
    }

    @Test
    void htmlToTextYieldsNothingWhenBlankOrEmptyAfterStripping() {
        assertTrue(DumTextNormalizers.htmlToText(null).isEmpty());
        assertTrue(DumTextNormalizers.htmlToText("").isEmpty());
        assertTrue(DumTextNormalizers.htmlToText("<p>   </p>").isEmpty(),
                "markup that strips to blank contributes nothing");
    }
}
