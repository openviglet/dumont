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

package com.viglet.dumont.connector.commons.domain;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;

/**
 * What a source declares about one site's field schema, sent ahead of its documents (roadmap
 * <strong>D63</strong>).
 *
 * <p>
 * The fields are the very {@code TurSNJobAttributeSpec}s published with every document, so there is
 * no second schema language to keep in step: whatever the source declares is what gets provisioned
 * and what gets indexed.
 *
 * @param siteName the site the documents will be published to
 * @param locale   the locale the source publishes in
 * @param fields   the declared fields, in the order they should exist
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */
public record DumIndexingSchema(String siteName, Locale locale,
        List<TurSNJobAttributeSpec> fields) {

    public DumIndexingSchema(String siteName, Locale locale, List<TurSNJobAttributeSpec> fields) {
        this.siteName = Objects.requireNonNull(siteName, "siteName");
        this.locale = locale;
        this.fields = fields != null ? List.copyOf(fields) : List.of();
    }

    /** Nothing to declare — a source with no field specs must not provision an empty schema. */
    public boolean isEmpty() {
        return fields.isEmpty();
    }
}
