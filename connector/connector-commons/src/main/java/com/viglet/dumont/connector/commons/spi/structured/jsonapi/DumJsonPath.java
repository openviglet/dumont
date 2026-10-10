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

package com.viglet.dumont.connector.commons.spi.structured.jsonapi;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The path language a JSON API manifest maps fields with (roadmap <strong>D92</strong>): dotted
 * keys with optional array indexes, e.g. {@code data.listVagas}, {@code company.display_name},
 * {@code locations[0].city}. A key applied to an array maps over its elements, so
 * {@code skills.name} reads every skill's name as a list. An empty path or {@code $} is the node
 * itself.
 *
 * <p>
 * Deliberately small: the manifests this serves name where a value sits, and a filter or wildcard
 * language would be a second thing to validate. It reads org.json, which connector-commons already
 * carries, so no JSONPath library joins every plugin's classpath.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumJsonPath {

    private static final Pattern SEGMENT = Pattern.compile("([^.\\[\\]]*)((?:\\[\\d+])*)");
    private static final Pattern INDEX = Pattern.compile("\\[(\\d+)]");

    private final String expression;
    private final List<Step> steps;

    private DumJsonPath(String expression, List<Step> steps) {
        this.expression = expression;
        this.steps = steps;
    }

    /**
     * Compiles a path.
     *
     * @throws IllegalArgumentException when a segment is malformed
     */
    public static DumJsonPath compile(String expression) {
        String path = expression == null ? "" : expression.trim();
        if (path.startsWith("$")) {
            path = path.substring(1);
            if (path.startsWith(".")) {
                path = path.substring(1);
            }
        }
        List<Step> steps = new ArrayList<>();
        if (!path.isEmpty()) {
            for (String segment : path.split("\\.", -1)) {
                Matcher matcher = SEGMENT.matcher(segment);
                if (segment.isEmpty() || !matcher.matches()) {
                    throw new IllegalArgumentException(
                            "Malformed JSON path '%s' at segment '%s'".formatted(expression, segment));
                }
                if (!matcher.group(1).isEmpty()) {
                    steps.add(new Step(matcher.group(1), -1));
                }
                Matcher index = INDEX.matcher(matcher.group(2));
                while (index.find()) {
                    steps.add(new Step(null, Integer.parseInt(index.group(1))));
                }
            }
        }
        return new DumJsonPath(expression, List.copyOf(steps));
    }

    /**
     * The value at this path: a scalar, a {@link List} when the path crossed an array, a
     * {@link JSONObject}/{@link JSONArray} when it stops on one, or {@code null} when absent.
     * JSON {@code null} reads as absent.
     */
    public Object read(Object node) {
        Object current = node;
        for (Step step : steps) {
            current = step.apply(current);
            if (current == null) {
                return null;
            }
        }
        return unwrap(current);
    }

    @Override
    public String toString() {
        return expression;
    }

    private static Object unwrap(Object value) {
        if (value == null || JSONObject.NULL.equals(value)) {
            return null;
        }
        if (value instanceof Mapped mapped) {
            List<Object> values = new ArrayList<>();
            mapped.values.forEach(v -> {
                Object unwrapped = unwrap(v);
                if (unwrapped instanceof List<?> nested) {
                    values.addAll(nested);
                } else if (unwrapped instanceof JSONArray array) {
                    // A mapped key that lands on arrays reads their elements, not the arrays.
                    for (int i = 0; i < array.length(); i++) {
                        Object item = unwrap(array.get(i));
                        if (item != null) {
                            values.add(item);
                        }
                    }
                } else if (unwrapped != null) {
                    values.add(unwrapped);
                }
            });
            return values;
        }
        return value;
    }

    /** The values a key produced across an array's elements, kept apart from a literal array. */
    private record Mapped(List<Object> values) {
    }

    private record Step(String key, int index) {

        Object apply(Object node) {
            if (node instanceof Mapped mapped) {
                List<Object> next = new ArrayList<>();
                mapped.values.forEach(v -> {
                    Object applied = apply(v);
                    if (applied instanceof Mapped nested) {
                        next.addAll(nested.values);
                    } else if (applied != null) {
                        next.add(applied);
                    }
                });
                return new Mapped(next);
            }
            if (key == null) {
                return node instanceof JSONArray array && index < array.length()
                        ? nullToAbsent(array.get(index))
                        : null;
            }
            if (node instanceof JSONObject object) {
                return object.has(key) ? nullToAbsent(object.get(key)) : null;
            }
            if (node instanceof JSONArray array) {
                List<Object> values = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    Object applied = apply(array.get(i));
                    if (applied != null) {
                        values.add(applied);
                    }
                }
                return new Mapped(values);
            }
            return null;
        }

        private static Object nullToAbsent(Object value) {
            return JSONObject.NULL.equals(value) ? null : value;
        }
    }
}
