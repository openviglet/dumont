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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSFacet;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;

/**
 * Reads a source's facet configuration ({@code facetPathPattern} and the
 * ordered list of {@link DumEDSFacet}s) and produces a name → value map for
 * a given page path. Adapter between the JPA shape and the pure matcher.
 */
@Component
public class DumEDSFacetDeriver {

    private final DumEDSPathPatternMatcher matcher;

    public DumEDSFacetDeriver(DumEDSPathPatternMatcher matcher) {
        this.matcher = matcher;
    }

    public Map<String, String> derive(String path, DumEDSSource source) {
        if (source == null || source.getFacetPathPattern() == null
                || source.getFacetPathPattern().isBlank()) {
            return Map.of();
        }
        return matcher.match(path, source.getFacetPathPattern(), facetSpecsOf(source));
    }

    static List<DumEDSPathPatternMatcher.FacetSpec> facetSpecsOf(DumEDSSource source) {
        if (source.getFacets() == null || source.getFacets().isEmpty()) {
            return List.of();
        }
        List<DumEDSFacet> ordered = new ArrayList<>(source.getFacets());
        ordered.sort(Comparator.comparingInt(DumEDSFacet::getOrdinal));
        List<DumEDSPathPatternMatcher.FacetSpec> specs = new ArrayList<>();
        for (DumEDSFacet facet : ordered) {
            specs.add(new DumEDSPathPatternMatcher.FacetSpec(facet.getName(),
                    parseAllowedValues(facet.getAllowedValues())));
        }
        return specs;
    }

    private static java.util.Set<String> parseAllowedValues(String csv) {
        if (csv == null || csv.isBlank()) {
            return java.util.Set.of();
        }
        return new LinkedHashSet<>(Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList());
    }
}
