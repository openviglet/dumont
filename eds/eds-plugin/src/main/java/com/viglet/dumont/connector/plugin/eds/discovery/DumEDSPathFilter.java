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

package com.viglet.dumont.connector.plugin.eds.discovery;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/**
 * Glob-style filter for EDS paths. Backed by Spring's {@link AntPathMatcher}, so
 * the supported syntax is Ant-style:
 *
 * <ul>
 *   <li>{@code *} — any sequence of characters within a single segment</li>
 *   <li>{@code **} — any number of path segments</li>
 *   <li>{@code ?} — single character</li>
 * </ul>
 *
 * The filter is path-relative (always starts with {@code /}). Paths and patterns
 * are normalised so callers don't have to be careful about leading slashes.
 */
@Component
public class DumEDSPathFilter {

    private final AntPathMatcher matcher = new AntPathMatcher();

    public boolean isExcluded(String path, Collection<String> patterns) {
        if (patterns == null || patterns.isEmpty() || path == null) {
            return false;
        }
        String normalised = normalise(path);
        for (String raw : patterns) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String pattern = normalise(raw);
            if (matcher.match(pattern, normalised)) {
                return true;
            }
        }
        return false;
    }

    public List<String> filter(Collection<String> paths, Collection<String> patterns) {
        return paths.stream().filter(p -> !isExcluded(p, patterns)).toList();
    }

    private static String normalise(String value) {
        String v = value.trim();
        return v.startsWith("/") ? v : "/" + v;
    }
}
