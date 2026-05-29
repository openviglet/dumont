/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.plugin.eds.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.plugin.eds.enrichment.DumEDSPathPatternMatcher.FacetSpec;

class DumEDSPathPatternMatcherTest {

    private static final String PATTERN = "/{locale}/{audience}/{section}/**";

    private final DumEDSPathPatternMatcher matcher = new DumEDSPathPatternMatcher();

    @Test
    void fullPathExtractsAllThreeFacets() {
        // Spec test case 4: /pt-br/pf/cartao-de-credito → locale=pt-br, audience=pf, section=...
        Map<String, String> result = matcher.match(
                "/pt-br/pf/cartao-de-credito", PATTERN, audienceFacets());

        assertThat(result)
                .containsEntry("locale", "pt-br")
                .containsEntry("audience", "pf")
                .containsEntry("section", "cartao-de-credito");
    }

    @Test
    void shortPathLeavesSectionEmpty() {
        // /pt-br/pj → locale=pt-br, audience=pj, section=(none)
        Map<String, String> result = matcher.match("/pt-br/pj", PATTERN, audienceFacets());

        assertThat(result)
                .containsEntry("locale", "pt-br")
                .containsEntry("audience", "pj")
                .doesNotContainKey("section");
    }

    @Test
    void pathWithoutAudienceSkipsToSection() {
        // /pt-br/sala-de-imprensa → locale=pt-br, audience=(none), section=sala-de-imprensa
        // Requires the audience vocabulary {pf,pj} so the matcher knows to skip.
        Map<String, String> result = matcher.match("/pt-br/sala-de-imprensa", PATTERN,
                audienceFacets());

        assertThat(result)
                .containsEntry("locale", "pt-br")
                .doesNotContainKey("audience")
                .containsEntry("section", "sala-de-imprensa");
    }

    @Test
    void deeperPathsAreConsumedByWildcard() {
        Map<String, String> result = matcher.match(
                "/pt-br/pf/cartao-de-credito/black/beneficios", PATTERN, audienceFacets());

        assertThat(result)
                .containsEntry("locale", "pt-br")
                .containsEntry("audience", "pf")
                .containsEntry("section", "cartao-de-credito");
        // anything past "section" is swallowed by **; no extra keys
        assertThat(result).hasSize(3);
    }

    @Test
    void literalSegmentsInPatternMustMatchExactly() {
        Map<String, String> result = matcher.match("/eds/pf/cartao",
                "/eds/{audience}/{section}",
                List.of(new FacetSpec("audience", Set.of("pf", "pj")),
                        new FacetSpec("section", Set.of())));

        assertThat(result)
                .containsEntry("audience", "pf")
                .containsEntry("section", "cartao");
    }

    @Test
    void literalMismatchReturnsEmpty() {
        Map<String, String> result = matcher.match("/wrong/pf/cartao",
                "/eds/{audience}/{section}",
                List.of(new FacetSpec("audience", Set.of()),
                        new FacetSpec("section", Set.of())));

        assertThat(result).isEmpty();
    }

    @Test
    void nullOrBlankInputsReturnEmpty() {
        assertThat(matcher.match(null, PATTERN, audienceFacets())).isEmpty();
        assertThat(matcher.match("/x", null, audienceFacets())).isEmpty();
        assertThat(matcher.match("/x", "", audienceFacets())).isEmpty();
        assertThat(matcher.match("/x", PATTERN, null)).isEmpty();
    }

    @Test
    void emptyVocabularyConsumesAnySegment() {
        // No vocabulary on locale or section → first segment always consumed by locale,
        // second by section.
        List<FacetSpec> facets = List.of(
                new FacetSpec("locale", Set.of()),
                new FacetSpec("section", Set.of()));

        Map<String, String> result = matcher.match("/anything/here",
                "/{locale}/{section}", facets);

        assertThat(result)
                .containsEntry("locale", "anything")
                .containsEntry("section", "here");
    }

    private static List<FacetSpec> audienceFacets() {
        return List.of(
                new FacetSpec("locale", Set.of()),
                new FacetSpec("audience", Set.of("pf", "pj")),
                new FacetSpec("section", Set.of()));
    }
}
