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

import java.util.List;
import java.util.Optional;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;

/**
 * The canonical-id index every run of every source of a provider consults (roadmap
 * <strong>D109</strong>), so cross-source resolution (D18) is not limited to the records one
 * {@code crawl()} holds in memory.
 *
 * <p>
 * A <em>member</em> is one source record filed under the canonical id its {@link DumCanonicalKey}
 * derived. A member is <em>live</em> while its source still has the record, and <em>held</em> once
 * its source's complete run no longer did: a held member contributes no values, but its source keeps
 * publishing the canonical document while another member is live, because a source's deindex sweep
 * removes what it tracked and did not publish again, whoever else still carries it. Once a canonical
 * id has no live member its rows are {@link #drop dropped} and the document leaves the index.
 *
 * <p>
 * {@link DumInMemoryResolutionIndex} keeps the index for the life of the plugin. The
 * {@link DumResolutionIndexFactory} bean {@code connector-app} registers builds one in the database,
 * which survives a restart; members are stored through {@link DumRecordCodec}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public interface DumResolutionIndex {

    /**
     * One source record under a canonical id.
     *
     * @param canonicalId the id the canonical document is published under
     * @param source      the source name
     * @param recordId    the record's own id in that source
     * @param rec         the extracted, enriched record as last seen
     * @param live        whether the source still has it
     * @param order       when the member was first filed, relative to the others: the merge takes
     *                    members in this order, so the first contributor wins in every run
     */
    record Member(String canonicalId, String source, String recordId, DumStructuredRecord rec,
            boolean live, long order) {
    }

    /**
     * File {@code rec} of {@code source} under {@code canonicalId} as live. A member the record had
     * under another canonical id (its key fields changed) becomes held there.
     */
    void put(String canonicalId, String source, String recordId, DumStructuredRecord rec);

    /** Every member of {@code canonicalId}, live and held, in {@link Member#order} order. */
    List<Member> members(String canonicalId);

    /** Every member {@code source} has filed, live and held. */
    List<Member> bySource(String source);

    /** The canonical id {@code source}'s record {@code recordId} is live under, if any. */
    Optional<String> canonicalIdOf(String source, String recordId);

    /** Mark {@code source}'s member {@code recordId} under {@code canonicalId} held. */
    void hold(String canonicalId, String source, String recordId);

    /** Remove every member of {@code canonicalId}. */
    void drop(String canonicalId);
}
