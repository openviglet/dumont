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

/**
 * One document the search index actually holds, as reported by the backend (roadmap
 * <strong>D64</strong>).
 *
 * <p>
 * The half of a content audit only the index can answer. The connector already knows what its
 * source has and what its own ledger recorded; this says what survived.
 *
 * @param id       the document id, matching the connector's {@code objectId}
 * @param version  the indexed document's version as epoch milliseconds, or {@code null} when it
 *                 carries none
 * @param checksum the content checksum the connector published, or {@code null} for a document
 *                 indexed before checksums or by a connector that computes none
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */
public record DumIndexInventoryEntry(String id, Long version, String checksum) {

    /**
     * True when this row and {@code ledgerChecksum} disagree about content.
     *
     * <p>
     * False when either side has no checksum: "unknown" is not "different", and treating it as
     * different would have the audit republish every document nobody can checksum.
     */
    public boolean differsFrom(String ledgerChecksum) {
        return checksum != null && ledgerChecksum != null && !checksum.equals(ledgerChecksum);
    }
}
