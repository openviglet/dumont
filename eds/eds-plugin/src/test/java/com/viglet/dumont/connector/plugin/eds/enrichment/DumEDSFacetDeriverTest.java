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

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSFacet;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;

class DumEDSFacetDeriverTest {

    private final DumEDSFacetDeriver deriver = new DumEDSFacetDeriver(new DumEDSPathPatternMatcher());

    @Test
    void readsFacetsInOrdinalOrder() {
        DumEDSSource source = source();
        // ordinals intentionally out-of-insertion-order to verify sort
        source.getFacets().add(new DumEDSFacet("audience", 1, "pf,pj", source));
        source.getFacets().add(new DumEDSFacet("section", 2, null, source));
        source.getFacets().add(new DumEDSFacet("locale", 0, null, source));

        Map<String, String> result = deriver.derive("/pt-br/pf/cartao", source);

        assertThat(result)
                .containsEntry("locale", "pt-br")
                .containsEntry("audience", "pf")
                .containsEntry("section", "cartao");
    }

    @Test
    void allowedValuesAsCsvSkipsNonMatchingSegment() {
        DumEDSSource source = source();
        source.getFacets().add(new DumEDSFacet("locale", 0, null, source));
        source.getFacets().add(new DumEDSFacet("audience", 1, "pf, pj", source));
        source.getFacets().add(new DumEDSFacet("section", 2, null, source));

        Map<String, String> result = deriver.derive("/pt-br/sala-de-imprensa", source);

        assertThat(result).doesNotContainKey("audience")
                .containsEntry("section", "sala-de-imprensa");
    }

    @Test
    void returnsEmptyWhenNoPatternConfigured() {
        DumEDSSource source = new DumEDSSource();
        source.setFacetPathPattern(null);

        assertThat(deriver.derive("/anything", source)).isEmpty();
    }

    @Test
    void returnsEmptyWhenNullSource() {
        assertThat(deriver.derive("/anything", null)).isEmpty();
    }

    private static DumEDSSource source() {
        DumEDSSource source = new DumEDSSource();
        source.setBaseUrl("https://example.invalid");
        source.setFacetPathPattern("/{locale}/{audience}/{section}/**");
        return source;
    }
}
