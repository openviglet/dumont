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

package com.viglet.dumont.connector.enrich;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** How the extractor reads the model's answer into candidates (roadmap D131). */
class DumTuringExtractorTest {

    @Test
    void oneCandidatePerLineWithListMarkersDropped() {
        assertThat(DumTuringExtractor.candidates("- Java\n* Spring Boot\n\n1. SQL\n2) Docker\n• Kafka"))
                .containsExactly("Java", "Spring Boot", "SQL", "Docker", "Kafka");
    }

    @Test
    void noneOrBlankIsNoCandidate() {
        assertThat(DumTuringExtractor.candidates("NONE")).isEmpty();
        assertThat(DumTuringExtractor.candidates(" none \n")).isEmpty();
        assertThat(DumTuringExtractor.candidates("")).isEmpty();
        assertThat(DumTuringExtractor.candidates(null)).isEmpty();
    }
}
