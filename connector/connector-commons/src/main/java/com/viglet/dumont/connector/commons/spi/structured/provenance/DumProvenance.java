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

import java.util.List;
import java.util.Map;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * The compact per-field provenance carried into every {@code TurSNJobItem} (roadmap
 * <strong>D16</strong>), so the search/RAG side can cite and audit where each attribute came from.
 *
 * <p>
 * Provenance ships as a single reserved multi-valued string attribute, {@link #FIELD}, with one
 * small {@link #token(String, String, DumFieldProvenance) token} per present field —
 * {@code field|source|strategy|selector|confidence}, trailing empty parts trimmed. A field with no
 * recorded origin emits no token, so the attribute stays a faithful subset of the document's grounded
 * fields. The declared {@link #spec() attribute spec} travels with the document like any other field.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public final class DumProvenance {

    /** The reserved attribute that carries the compact per-field provenance tokens. */
    public static final String FIELD = "dum_provenance";

    private static final char SEP = '|';

    private DumProvenance() {
    }

    /**
     * The provenance tokens for a record's fields, in record order — one per field that carries an
     * origin. Empty when the record has no provenance, so no reserved attribute is emitted.
     */
    public static List<String> tokens(String source, DumStructuredRecord record) {
        return record.provenance().entrySet().stream()
                .map(entry -> token(entry.getKey(), source, entry.getValue()))
                .toList();
    }

    /** One compact token {@code field|source|strategy|selector|confidence} (trailing blanks trimmed). */
    public static String token(String field, String source, DumFieldProvenance provenance) {
        String[] parts = {
                nullToEmpty(field),
                nullToEmpty(source),
                provenance == null ? "" : nullToEmpty(provenance.strategy()),
                provenance == null ? "" : nullToEmpty(provenance.selector()),
                provenance == null || provenance.confidence() == null ? ""
                        : String.valueOf(provenance.confidence())
        };
        int last = parts.length - 1;
        while (last > 0 && parts[last].isEmpty()) {
            last--;
        }
        StringBuilder token = new StringBuilder(parts[0]);
        for (int i = 1; i <= last; i++) {
            token.append(SEP).append(parts[i]);
        }
        return token.toString();
    }

    /** The Turing attribute spec for the reserved provenance field (multi-valued, non-facet string). */
    public static TurSNJobAttributeSpec spec() {
        return TurSNJobAttributeSpec.builder()
                .name(FIELD)
                .type(TurSEFieldType.STRING)
                .multiValued(true)
                .facet(false)
                .mandatory(false)
                .description("Field-level provenance: field|source|strategy|selector|confidence")
                .build();
    }

    /**
     * Add the compact provenance attribute + spec to a record's would-be document, when the record
     * carries any provenance. A no-op (the maps are returned unchanged) otherwise.
     */
    public static void contribute(String source, DumStructuredRecord record,
            Map<String, Object> attributes, List<TurSNJobAttributeSpec> specs) {
        List<String> tokens = tokens(source, record);
        if (tokens.isEmpty()) {
            return;
        }
        attributes.put(FIELD, tokens);
        specs.add(spec());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
