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

package com.viglet.dumont.connector.commons.spi.structured.strategy;

import java.util.regex.Pattern;

/**
 * A classification rule for {@link DumSitemapStrategy}: a URL matching {@link #pattern()} belongs to
 * {@link #level()}. Rules are evaluated in order and the first match wins — the same shape cursarei's
 * crawler engine uses to split a flat {@code sitemap.xml} into levels (undergraduate vs graduate,
 * listing vs detail, …).
 *
 * @param pattern the URL pattern (matched with {@link java.util.regex.Matcher#find()})
 * @param level   the level a matching URL is classified into
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record DumUrlLevelRule(Pattern pattern, String level) {

    public DumUrlLevelRule {
        if (pattern == null) {
            throw new IllegalArgumentException("URL level rule pattern is required");
        }
        if (level == null || level.isBlank()) {
            throw new IllegalArgumentException("URL level rule level is required");
        }
    }

    /** Convenience factory compiling {@code regex} into a {@link Pattern}. */
    public static DumUrlLevelRule of(String regex, String level) {
        return new DumUrlLevelRule(Pattern.compile(regex), level);
    }

    boolean matches(String url) {
        return pattern.matcher(url).find();
    }
}
