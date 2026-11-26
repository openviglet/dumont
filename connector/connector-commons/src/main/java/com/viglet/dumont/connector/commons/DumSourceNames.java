/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.dumont.connector.commons;

import java.util.regex.Pattern;

/**
 * The rule a source name has to follow (roadmap <strong>DU6</strong>).
 *
 * <p>A source name is the path segment of {@code /api/v2/connector/index/{name}}, {@code reindex},
 * {@code audit} and the monitoring endpoints, so a name holding whitespace saves fine and then fails
 * on its first run. The console refuses it in every form (DU3); this is the same rule for what reaches
 * the server without a form: the source APIs, which answer 400, and the imports, which skip the
 * source and log why.
 */
public final class DumSourceNames {

    /** What an API refusal and an import log line say. */
    public static final String RULE = "A source name cannot contain whitespace: it is the path segment"
            + " of the index, reindex, audit and monitoring URLs.";

    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    private DumSourceNames() {
    }

    /**
     * Whether {@code name} may be saved as a source name. A null name passes: whether a name is
     * required is each source's own validation, not this rule's.
     */
    public static boolean isValid(String name) {
        return name == null || !WHITESPACE.matcher(name).find();
    }
}
