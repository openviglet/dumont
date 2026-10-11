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

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance;

/**
 * How the live members of one canonical id become its published document (roadmap
 * <strong>D110</strong>). A provider declares it by overriding
 * {@code DumStructuredSourcePlugin.survivorship()}; the default is {@link #fieldMerge()}.
 *
 * <ul>
 * <li>{@link #fieldMerge()}: the D18 rule. The first member filed is the base, and later ones only
 * fill the fields it lacks.</li>
 * <li>{@link #fieldMerge(Map)}: field by field, with a member order per field. A field with no rule
 * takes the first-filed member that has it.</li>
 * <li>{@link #wholeRecord(Comparator)}: one member is published whole, the first by the comparator.
 * No partner's field is mixed into another's record.</li>
 * </ul>
 *
 * <p>
 * Every rule goes through the schema-bound builder ({@link DumRecordMerge}), so nothing is invented,
 * and every field's provenance names the source that supplied it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
@FunctionalInterface
public interface DumSurvivorship {

    /**
     * The document published under {@code canonicalId}.
     *
     * @param members the live members, never empty, in the order they were first filed
     */
    DumStructuredRecord survive(String canonicalId, List<DumResolutionIndex.Member> members);

    /** The first member filed is the base; later members fill only the fields it lacks. */
    static DumSurvivorship fieldMerge() {
        return (canonicalId, members) -> {
            DumStructuredRecord document = null;
            for (DumResolutionIndex.Member member : members) {
                document = document == null
                        ? DumRecordMerge.rekey(member.rec(), canonicalId, member.source())
                        : DumRecordMerge.merge(document, member.rec(), member.source());
            }
            return document;
        };
    }

    /**
     * Field by field: each field comes from the first member, in its rule's order, that has it. A
     * field with no rule follows the first-filed order. The schema is the first-filed member's.
     */
    static DumSurvivorship fieldMerge(Map<String, Comparator<DumResolutionIndex.Member>> rules) {
        return (canonicalId, members) -> {
            DumStructuredRecord base = members.getFirst().rec();
            DumStructuredRecord.Builder builder = DumStructuredRecord.builder(canonicalId, base.schema())
                    .contentType(base.contentType());
            base.schema().fields().forEach(field -> {
                Comparator<DumResolutionIndex.Member> rule = rules.get(field.name());
                List<DumResolutionIndex.Member> ordered = rule == null ? members
                        : members.stream().sorted(rule).toList();
                ordered.stream().filter(member -> member.rec().has(field.name())).findFirst()
                        .ifPresent(member -> builder.set(field.name(), member.rec().values().get(field.name()),
                                stamp(member.rec().provenance().get(field.name()), member.source())));
            });
            // Transients follow the base, as in DumRecordMerge: the first-filed member's win.
            members.reversed().forEach(member -> member.rec().transients().forEach(builder::transientValue));
            return builder.build();
        };
    }

    /**
     * One member whole: the first by {@code preference}, ties broken by the order members were
     * first filed. Its fields, and only its fields, are published.
     */
    static DumSurvivorship wholeRecord(Comparator<DumResolutionIndex.Member> preference) {
        return (canonicalId, members) -> {
            DumResolutionIndex.Member winner = members.stream().sorted(preference).findFirst().orElseThrow();
            return DumRecordMerge.rekey(winner.rec(), canonicalId, winner.source());
        };
    }

    private static DumFieldProvenance stamp(DumFieldProvenance provenance, String source) {
        return provenance == null ? null : provenance.withSource(source);
    }
}
