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
 * {@link DumProvenance}), so the search/RAG side can cite and audit the origin of each attribute.
 *
 * <p>
 * <strong>Source (roadmap D16 / D18).</strong> Within a single-source record every field shares the
 * record's source, so {@code source} stays {@code null} here and is stamped in once at emission time.
 * Cross-source entity resolution (D18) merges fields from several sources into one canonical
 * document; there each merged-in field carries its own {@code source} via {@link #withSource(String)}
 * so the emitted token cites the source that actually won that field — never a single ambiguous
 * source for the merged whole.
 *
 * @param strategy   how the value was produced (e.g. {@code "css"}, {@code "tabular"},
 *                   {@code "enrichment:region"}); never blank
 * @param selector   the selector/path the value was read from (a CSS selector, a column locator),
 *                   or {@code null} when the strategy has no locator (e.g. a derived field)
 * @param confidence an optional 0–1 confidence, or {@code null} when not applicable
 * @param source     the source that supplied this field, or {@code null} when it is the record's own
 *                   source (stamped at emission time); set only when a merge mixes sources (D18)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public record DumFieldProvenance(String strategy, String selector, Double confidence, String source) {

    /** The strategy name used for values an enricher derived. */
    public static final String ENRICHMENT_STRATEGY = "enrichment";

    public DumFieldProvenance {
        if (strategy == null || strategy.isBlank()) {
            throw new IllegalArgumentException("Provenance strategy is required");
        }
        strategy = strategy.trim();
        selector = (selector == null || selector.isBlank()) ? null : selector.trim();
        source = (source == null || source.isBlank()) ? null : source.trim();
    }

    /** A CSS-selector origin (the detail-page / listing extractor). */
    public static DumFieldProvenance css(String selector) {
        return new DumFieldProvenance("css", selector, null, null);
    }

    /** A tabular-column origin (each row a record; {@code locator} names the column). */
    public static DumFieldProvenance tabular(String locator) {
        return new DumFieldProvenance("tabular", locator, null, null);
    }

    /**
     * An AEM extraction origin (roadmap D21); {@code locator} names the target attribute the value
     * was extracted into from the content node's JCR properties / component tree.
     */
    public static DumFieldProvenance aem(String locator) {
        return new DumFieldProvenance("aem", locator, null, null);
    }

    /**
     * A filesystem origin (roadmap D22, Assets connector); {@code locator} names the file facet the
     * value was read from (e.g. {@code "path"}, {@code "name"}, {@code "lastModified"},
     * {@code "size"}, {@code "tika:content"}).
     */
    public static DumFieldProvenance filesystem(String locator) {
        return new DumFieldProvenance("filesystem", locator, null, null);
    }

    /** A value an enricher derived (roadmap D04); {@code name} identifies the enricher. */
    public static DumFieldProvenance enrichment(String name) {
        String suffix = (name == null || name.isBlank()) ? "" : ":" + name.trim();
        return new DumFieldProvenance(ENRICHMENT_STRATEGY + suffix, null, null, null);
    }

    /** This provenance with a confidence attached (the value/selector/source are unchanged). */
    public DumFieldProvenance withConfidence(Double confidence) {
        return new DumFieldProvenance(strategy, selector, confidence, source);
    }

    /**
     * This provenance tagged with the {@code source} that supplied the field (roadmap D18) — used
     * when a cross-source merge folds fields from several sources into one canonical document so each
     * field still cites the source it actually came from. The strategy/selector/confidence are
     * unchanged.
     */
    public DumFieldProvenance withSource(String source) {
        return new DumFieldProvenance(strategy, selector, confidence, source);
    }
}
