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

package com.viglet.dumont.connector.commons.logging;

import java.util.LinkedHashMap;
import java.util.Map;

import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.turing.client.sn.job.TurSNJobItem;

/**
 * How a job item's source and run survive the queue (roadmap <strong>D71</strong>).
 *
 * <p>
 * The indexing log records a document's whole passage — prepared, sent to the queue, delivered to
 * the search engine — and every row carries the source and transaction id so a reader can filter
 * by one and follow the other. Up to the queue that comes from the session. Past it there is no
 * session: the JMS message is a {@code TurSNJobItems}, and an item carries sites, locale, checksum
 * and a {@code sourceApps} attribute holding the <em>provider</em> — never the source name.
 *
 * <p>
 * So the two values travel on the item. Per item rather than per message, because a drain covers
 * whatever is buffered and that spans sessions (D69) — one label on the envelope would name the
 * wrong source for everything else in it.
 *
 * <p>
 * They are connector bookkeeping, not content, so {@link #strip(Map)} takes them off again on the
 * far side, before the batch reaches an indexing plugin. Unlike {@code dum_provenance}, which is
 * reserved <em>and published</em>, these two exist only between the buffer and the listener and
 * must never become fields on a document.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */
public final class DumJobTrail {

    /** Reserved attribute carrying the source that produced the item. */
    public static final String SOURCE = "dum_trail_source";
    /** Reserved attribute carrying the transaction id of the run that produced it. */
    public static final String TRANSACTION_ID = "dum_trail_transaction";

    private DumJobTrail() {
        throw new IllegalStateException("Job trail utility class");
    }

    /**
     * Writes the session's source and transaction id onto the item, just before it is sent.
     *
     * <p>
     * The attributes are replaced rather than mutated: a plugin is free to have built its map with
     * {@code Map.of}, and a stamp that threw on an immutable map would fail the whole batch.
     */
    public static void stamp(TurSNJobItem item, DumConnectorSession session) {
        if (item == null || session == null) {
            return;
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (item.getAttributes() != null) {
            attributes.putAll(item.getAttributes());
        }
        putIfPresent(attributes, SOURCE, session.getSource());
        putIfPresent(attributes, TRANSACTION_ID, session.getTransactionId());
        item.setAttributes(attributes);
    }

    /** The source that produced the item, or {@code null} on one that predates the stamp. */
    public static String source(TurSNJobItem item) {
        return item == null ? null : item.getStringAttribute(SOURCE);
    }

    /** The transaction id of the run that produced the item, or {@code null}. */
    public static String transactionId(TurSNJobItem item) {
        return item == null ? null : item.getStringAttribute(TRANSACTION_ID);
    }

    /**
     * The same attributes without the trail, for the hand-off to an indexing plugin — bookkeeping
     * that reached the backend would become two fields on every document.
     */
    public static Map<String, Object> strip(Map<String, Object> attributes) {
        if (attributes == null) {
            return null;
        }
        Map<String, Object> stripped = new LinkedHashMap<>(attributes);
        stripped.remove(SOURCE);
        stripped.remove(TRANSACTION_ID);
        return stripped;
    }

    private static void putIfPresent(Map<String, Object> attributes, String key, String value) {
        if (value != null) {
            attributes.put(key, value);
        }
    }
}
