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

package com.viglet.dumont.connector.commons.spi.structured.infer;

/**
 * The shape of a structured source, as classified by the AI-assisted inference (roadmap
 * <strong>D13</strong>). Each value (except {@link #UNKNOWN}) maps to one of the declarative D02
 * extraction strategies; the inferred {@link DumSourceDraft} is materialized against the matching
 * strategy once a human confirms it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.2
 */
public enum DumSourceShape {

    /** A {@code sitemap.xml} (or sitemap index) — {@code DumSitemapStrategy}. */
    SITEMAP,

    /** A listing page whose anchors point to per-item detail pages — {@code DumHtmlListingStrategy}. */
    HTML_LISTING,

    /** A {@code <table>}-style catalog where each row is a record — {@code DumTabularListingStrategy}. */
    TABULAR_LISTING,

    /** The shape could not be grounded — inference declines rather than guesses. */
    UNKNOWN;

    /**
     * Tolerant parse of a model-supplied label into a shape. Hyphens, spaces and case are ignored
     * (so {@code "tabular listing"}, {@code "TABULAR_LISTING"} and {@code "Tabular"} all match), and
     * any unrecognized or blank label degrades to {@link #UNKNOWN} rather than throwing — inference
     * never fabricates a shape it was not given.
     */
    public static DumSourceShape fromLabel(String label) {
        if (label == null) {
            return UNKNOWN;
        }
        String normalized = label.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "SITEMAP" -> SITEMAP;
            case "HTML_LISTING", "HTML", "LISTING" -> HTML_LISTING;
            case "TABULAR_LISTING", "TABULAR", "TABLE" -> TABULAR_LISTING;
            default -> UNKNOWN;
        };
    }
}
