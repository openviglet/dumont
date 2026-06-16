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

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * One <em>proposed</em> field of an inferred {@link DumSourceDraft} (roadmap <strong>D13</strong>):
 * the manifest spec a human will edit and confirm ({@code name}, {@code type}, {@code multiValued},
 * {@code facet}, {@code mandatory}) plus the <em>structural</em> extraction hint that tells the D02
 * strategy where to read the value from ({@code columnIndex} or {@code selector} + {@code attribute}).
 *
 * <p>
 * <strong>Grounding.</strong> The inference proposes <em>structure</em> only — which column or
 * selector a value comes from — never the value itself. The actual content is always read from the
 * live source at extraction time; the model never supplies it. A field with a blank name is invalid
 * and is dropped by {@link DumSourceDraft}, never published as a placeholder.
 *
 * @param name        the field name in the indexed document (required)
 * @param type        the search-engine field type (defaults to {@link TurSEFieldType#STRING})
 * @param multiValued whether the field may hold more than one value
 * @param facet       whether the field is exposed as a facet
 * @param mandatory   whether a record without this field is invalid (skipped, never fabricated)
 * @param description an optional human-readable description
 * @param columnIndex zero-based cell position for a tabular column, or {@code null} when a selector
 *                    is used
 * @param selector    a CSS selector for the value (within a row, or on a detail page); overrides
 *                    {@code columnIndex} when set
 * @param attribute   the attribute to read (e.g. {@code "abs:href"}), or {@code null}/blank to read
 *                    the element text
 *
 * @author Alexandre Oliveira
 * @since 2026.3.2
 */
public record DumInferredField(String name, TurSEFieldType type, boolean multiValued, boolean facet,
        boolean mandatory, String description, Integer columnIndex, String selector,
        String attribute) {

    public DumInferredField {
        if (type == null) {
            type = TurSEFieldType.STRING;
        }
    }

    /** Whether this field carries a usable name (a blank-named proposal is dropped from a draft). */
    public boolean hasName() {
        return name != null && !name.isBlank();
    }

    /** Whether a CSS selector was proposed for this field's value. */
    public boolean hasSelector() {
        return selector != null && !selector.isBlank();
    }

    /** The manifest declaration for this field — the spec confirmed into a {@code DumStructuredSchema}. */
    public DumStructuredField toStructuredField() {
        return DumStructuredField.builder(name)
                .type(type)
                .multiValued(multiValued)
                .facet(facet)
                .mandatory(mandatory)
                .description(description)
                .build();
    }
}
