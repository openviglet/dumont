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

package com.viglet.dumont.connector.commons.spi.structured.resolve;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance;

/**
 * The grounding-safe merge that folds records sharing a canonical id into one document (roadmap
 * <strong>D18</strong>).
 *
 * <p>
 * The rule is deliberately conservative and auditable:
 *
 * <ul>
 * <li><strong>First contributor wins.</strong> A field already present on the running merge is never
 * overwritten — a later source may only fill a field the merge still lacks. So the source that first
 * <em>declared</em> a value keeps it, and a disagreement never silently replaces data.</li>
 * <li><strong>Never invent.</strong> Every value flows back through the schema-bound
 * {@link DumStructuredRecord.Builder}, so a merged-in field must be declared by the canonical
 * document's schema and survive grounding; nothing absent is fabricated, nothing blank is published,
 * no undeclared field is injected.</li>
 * <li><strong>Stays auditable.</strong> Each merged-in field carries its origin
 * {@link DumFieldProvenance#withSource(String) tagged with the source that supplied it}, so the
 * published {@code dum_provenance} cites, per field, which source won.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public final class DumRecordMerge {

    private DumRecordMerge() {
    }

    /**
     * Re-key {@code record} under {@code canonicalId} as the first contributor to its canonical
     * document, tagging every field's provenance with {@code source}. The schema and values are
     * unchanged; only the id and the provenance source are rewritten.
     */
    public static DumStructuredRecord rekey(DumStructuredRecord rec, String canonicalId,
            String source) {
        DumStructuredRecord.Builder builder =
                DumStructuredRecord.builder(canonicalId, rec.schema());
        rec.values().forEach((field, value) ->
                builder.set(field, value, stamp(rec.provenance().get(field), source)));
        rec.transients().forEach(builder::transientValue);
        return builder.build();
    }

    /**
     * Merge {@code incoming} into the running {@code base} canonical document. {@code base} keeps its
     * id and schema and wins every field it already carries; {@code incoming} contributes only the
     * fields {@code base} still lacks <em>and</em> the base schema declares, each tagged with
     * {@code incomingSource}.
     */
    public static DumStructuredRecord merge(DumStructuredRecord base, DumStructuredRecord incoming,
            String incomingSource) {
        DumStructuredRecord.Builder builder =
                DumStructuredRecord.builder(base.id(), base.schema());
        base.values().forEach((field, value) ->
                builder.set(field, value, base.provenance().get(field)));
        incoming.values().forEach((field, value) -> {
            if (base.has(field) || !base.schema().hasField(field)) {
                return;
            }
            builder.set(field, value, stamp(incoming.provenance().get(field), incomingSource));
        });
        // Transients follow the fields' rule (D97): the base's win, the incoming fill the gaps.
        incoming.transients().forEach(builder::transientValue);
        base.transients().forEach(builder::transientValue);
        return builder.build();
    }

    private static DumFieldProvenance stamp(DumFieldProvenance provenance, String source) {
        return provenance == null ? null : provenance.withSource(source);
    }
}
