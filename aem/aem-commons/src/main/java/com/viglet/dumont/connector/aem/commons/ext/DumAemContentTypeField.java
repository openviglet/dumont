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

package com.viglet.dumont.connector.aem.commons.ext;

import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * One declared field of a {@link DumAemContentType} (roadmap <strong>D25</strong>) — name, type and
 * the facet/multi-valued/mandatory flags that make up a per-content-type schema.
 *
 * <p>
 * It is the AEM ext-SPI counterpart of the structured-source {@code DumStructuredField}: the
 * {@code aem-commons} module declares content types in code without depending on the connector
 * commons, and the AEM structured-source adapter translates these descriptors into the typed schema
 * each record is bound to. Defaults are the common case — a single-valued {@link TurSEFieldType#STRING
 * STRING} that is neither faceted nor mandatory.
 *
 * @param name        the published attribute name
 * @param type        the field type (defaults to {@link TurSEFieldType#STRING} via the factories)
 * @param multiValued whether the field carries more than one value
 * @param facet       whether the field is exposed as a search facet
 * @param mandatory   whether a record without this field is dropped (grounding)
 * @param description an optional human description
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record DumAemContentTypeField(String name, TurSEFieldType type, boolean multiValued,
        boolean facet, boolean mandatory, String description) {

    public DumAemContentTypeField {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Content-type field name is required");
        }
        if (type == null) {
            type = TurSEFieldType.STRING;
        }
    }

    /** A single-valued {@link TurSEFieldType#STRING STRING} field. */
    public static DumAemContentTypeField string(String name) {
        return new DumAemContentTypeField(name, TurSEFieldType.STRING, false, false, false, null);
    }

    /** A single-valued {@link TurSEFieldType#TEXT TEXT} field. */
    public static DumAemContentTypeField text(String name) {
        return new DumAemContentTypeField(name, TurSEFieldType.TEXT, false, false, false, null);
    }

    /** A multi-valued, faceted {@link TurSEFieldType#STRING STRING} field. */
    public static DumAemContentTypeField facet(String name) {
        return new DumAemContentTypeField(name, TurSEFieldType.STRING, true, true, false, null);
    }

    /** A typed field. */
    public static DumAemContentTypeField of(String name, TurSEFieldType type) {
        return new DumAemContentTypeField(name, type, false, false, false, null);
    }

    /** A copy of this field declared mandatory. */
    public DumAemContentTypeField asMandatory() {
        return new DumAemContentTypeField(name, type, multiValued, facet, true, description);
    }

    /** A copy of this field declared multi-valued. */
    public DumAemContentTypeField asMultiValued() {
        return new DumAemContentTypeField(name, type, true, facet, mandatory, description);
    }
}
