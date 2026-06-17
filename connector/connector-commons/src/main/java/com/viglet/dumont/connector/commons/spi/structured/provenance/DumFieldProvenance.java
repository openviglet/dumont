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

package com.viglet.dumont.connector.commons.spi.structured.provenance;

/**
 * Where one published field value came from (roadmap <strong>D16</strong>): the extraction
 * {@code strategy} that produced it, the {@code selector}/path it was read from, and an optional
 * {@code confidence}.
 *
 * <p>
 * A provenance is attached to a field as it is set on a {@code DumStructuredRecord}, so only fields
 * the source actually supplied carry one — the same grounding contract the value itself obeys. It is
 * deliberately <em>compact</em>: it travels with every document into the {@code TurSNJobItem} (see
 * {@link DumProvenance}), so the search/RAG side can cite and audit the origin of each attribute. The
 * source name is not stored here — it is the same for every field of a record and is stamped in at
 * emission time.
 *
 * @param strategy   how the value was produced (e.g. {@code "css"}, {@code "tabular"},
 *                   {@code "enrichment:region"}); never blank
 * @param selector   the selector/path the value was read from (a CSS selector, a column locator),
 *                   or {@code null} when the strategy has no locator (e.g. a derived field)
 * @param confidence an optional 0–1 confidence, or {@code null} when not applicable
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public record DumFieldProvenance(String strategy, String selector, Double confidence) {

    /** The strategy name used for values an enricher derived. */
    public static final String ENRICHMENT = "enrichment";

    public DumFieldProvenance {
        if (strategy == null || strategy.isBlank()) {
            throw new IllegalArgumentException("Provenance strategy is required");
        }
        strategy = strategy.trim();
        selector = (selector == null || selector.isBlank()) ? null : selector.trim();
    }

    /** A CSS-selector origin (the detail-page / listing extractor). */
    public static DumFieldProvenance css(String selector) {
        return new DumFieldProvenance("css", selector, null);
    }

    /** A tabular-column origin (each row a record; {@code locator} names the column). */
    public static DumFieldProvenance tabular(String locator) {
        return new DumFieldProvenance("tabular", locator, null);
    }

    /** A value an enricher derived (roadmap D04); {@code name} identifies the enricher. */
    public static DumFieldProvenance enrichment(String name) {
        String suffix = (name == null || name.isBlank()) ? "" : ":" + name.trim();
        return new DumFieldProvenance(ENRICHMENT + suffix, null, null);
    }

    /** This provenance with a confidence attached (the value/selector are unchanged). */
    public DumFieldProvenance withConfidence(Double confidence) {
        return new DumFieldProvenance(strategy, selector, confidence);
    }
}
