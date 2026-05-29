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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Pure-logic matcher: given a pattern like {@code /{locale}/{audience}/{section}/**}
 * and a path, returns a name → value map for the named groups. {@code **} (which
 * may appear only at the end) consumes any remaining path segments.
 *
 * <p>An optional vocabulary makes the matcher tolerate paths that skip earlier
 * facets: if a facet declares an {@code allowedValues} set and the next path
 * segment is not in it, the facet is left empty and the segment is offered to
 * the next named group. This is required to make:
 *
 * <ul>
 *   <li>{@code /pt-br/pf/cartao} → {@code locale=pt-br, audience=pf, section=cartao}</li>
 *   <li>{@code /pt-br/pj} → {@code locale=pt-br, audience=pj}</li>
 *   <li>{@code /pt-br/sala-de-imprensa} (with audience vocab pf|pj)
 *       → {@code locale=pt-br, section=sala-de-imprensa}</li>
 * </ul>
 *
 * all behave as the spec demands.
 */
@Component
public class DumEDSPathPatternMatcher {

    public static final String WILDCARD_REST = "**";

    public record FacetSpec(String name, Set<String> allowedValues) {

        public FacetSpec {
            allowedValues = allowedValues == null || allowedValues.isEmpty()
                    ? Set.of()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(allowedValues));
        }

        public boolean hasVocabulary() {
            return !allowedValues.isEmpty();
        }

        public boolean accepts(String segment) {
            return !hasVocabulary() || allowedValues.contains(segment);
        }
    }

    public Map<String, String> match(String path, String pattern, List<FacetSpec> facetOrder) {
        Map<String, String> result = new LinkedHashMap<>();
        if (path == null || pattern == null || pattern.isBlank() || facetOrder == null) {
            return result;
        }
        List<String> patternSegments = splitToSegments(pattern);
        List<String> pathSegments = splitToSegments(path);
        Map<String, FacetSpec> byName = new LinkedHashMap<>();
        for (FacetSpec spec : facetOrder) {
            byName.put(spec.name(), spec);
        }
        int pathIndex = 0;
        for (String patternSeg : patternSegments) {
            if (WILDCARD_REST.equals(patternSeg)) {
                break;
            }
            if (isNamedGroup(patternSeg)) {
                String name = patternSeg.substring(1, patternSeg.length() - 1);
                FacetSpec spec = byName.getOrDefault(name, new FacetSpec(name, Set.of()));
                if (pathIndex >= pathSegments.size()) {
                    continue;
                }
                String candidate = pathSegments.get(pathIndex);
                if (!spec.accepts(candidate)) {
                    continue;
                }
                result.put(name, candidate);
                pathIndex++;
            } else if (pathIndex < pathSegments.size()
                    && patternSeg.equals(pathSegments.get(pathIndex))) {
                pathIndex++;
            } else {
                // literal mismatch — pattern does not apply to this path
                return Collections.emptyMap();
            }
        }
        return result;
    }

    static boolean isNamedGroup(String segment) {
        return segment != null && segment.length() > 2 && segment.charAt(0) == '{'
                && segment.charAt(segment.length() - 1) == '}';
    }

    static List<String> splitToSegments(String pathOrPattern) {
        String trimmed = pathOrPattern.trim();
        if (trimmed.isEmpty() || "/".equals(trimmed)) {
            return List.of();
        }
        return Arrays.stream(trimmed.split("/"))
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
