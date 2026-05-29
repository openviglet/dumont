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

package com.viglet.dumont.connector.eds.sample.ext;

import java.util.Locale;

import com.viglet.dumont.connector.eds.commons.DumEDSContext;
import com.viglet.dumont.connector.eds.commons.ext.DumEDSExtLocaleInterface;

/**
 * Resolves a {@link Locale} from the first path segment. Matches the convention
 * used by many EDS sites that namespace by locale at the top of the URL:
 * {@code /pt-br/...} → {@code pt-BR}, {@code /en-us/...} → {@code en-US}.
 *
 * <p>Behaviour:
 *
 * <ul>
 *   <li>First path segment with a {@code xx-yy} shape → parsed as a BCP-47 tag</li>
 *   <li>First segment is a 2-letter code only (e.g. {@code /pt/...}) → language-only locale</li>
 *   <li>Anything else (no segment, single segment that isn't a locale code) → {@code Locale.US}</li>
 * </ul>
 *
 * <p>Usage: configure on the EDS source via {@code localeClass =
 * "com.viglet.dumont.connector.eds.sample.ext.DumEDSExtPathLocale"} and leave
 * {@code locale} unset. The plugin process instantiates this class (cached via
 * {@code DumCustomClassCache}) and calls {@link #consume} per page.
 */
public class DumEDSExtPathLocale implements DumEDSExtLocaleInterface {

    @Override
    public Locale consume(DumEDSContext context) {
        if (context == null || context.getPath() == null || context.getPath().isBlank()) {
            return Locale.US;
        }
        String trimmed = context.getPath().startsWith("/")
                ? context.getPath().substring(1)
                : context.getPath();
        int slash = trimmed.indexOf('/');
        String firstSegment = slash >= 0 ? trimmed.substring(0, slash) : trimmed;
        if (firstSegment.isEmpty()) {
            return Locale.US;
        }
        return localeFromSegment(firstSegment);
    }

    static Locale localeFromSegment(String segment) {
        if (looksLikeLanguageRegion(segment)) {
            return Locale.forLanguageTag(canonicalise(segment));
        }
        if (looksLikeLanguageOnly(segment)) {
            return Locale.forLanguageTag(segment.toLowerCase(Locale.ROOT));
        }
        return Locale.US;
    }

    private static boolean looksLikeLanguageRegion(String segment) {
        return segment.length() == 5 && segment.charAt(2) == '-'
                && Character.isLetter(segment.charAt(0))
                && Character.isLetter(segment.charAt(1))
                && Character.isLetter(segment.charAt(3))
                && Character.isLetter(segment.charAt(4));
    }

    private static boolean looksLikeLanguageOnly(String segment) {
        return segment.length() == 2
                && Character.isLetter(segment.charAt(0))
                && Character.isLetter(segment.charAt(1));
    }

    /** Normalises {@code pt-br} → {@code pt-BR} so language-tag parsing yields the canonical form. */
    private static String canonicalise(String segment) {
        return segment.substring(0, 2).toLowerCase(Locale.ROOT)
                + "-"
                + segment.substring(3).toUpperCase(Locale.ROOT);
    }
}
