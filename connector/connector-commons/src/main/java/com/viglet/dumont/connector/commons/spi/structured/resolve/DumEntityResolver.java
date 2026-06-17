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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;

/**
 * Accumulates records from one or more sources and collapses those that share a
 * {@link DumCanonicalKey canonical id} into a single document (roadmap <strong>D18</strong>).
 *
 * <p>
 * Fed one extracted (and already enriched) record at a time across every source of a crawl, it keeps
 * a running, grounding-safe {@link DumRecordMerge merge} per canonical id. {@link #entities()} then
 * yields exactly one {@link Entity} per real-world entity: the merged canonical document plus the
 * <em>primary</em> source (the first that contributed it), under whose sites/locale the document is
 * published.
 *
 * <p>
 * A record the key cannot resolve (no value for the key field) is <strong>passed through</strong>
 * unchanged on its own id — it is never merged on a guessed identity. Insertion order is preserved so
 * a crawl publishes deterministically.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public final class DumEntityResolver {

    /**
     * A resolved entity ready to publish: the merged canonical {@code record} and the {@code source}
     * under whose sites/locale it is indexed (the first source that contributed it).
     */
    public record Entity(DumStructuredSource source, DumStructuredRecord record) {
    }

    private final DumCanonicalKey canonicalKey;
    private final Map<String, Entity> merged = new LinkedHashMap<>();
    private final List<Entity> passthrough = new ArrayList<>();

    public DumEntityResolver(DumCanonicalKey canonicalKey) {
        if (canonicalKey == null) {
            throw new IllegalArgumentException("A canonical key is required for entity resolution");
        }
        this.canonicalKey = canonicalKey;
    }

    /** Feed one extracted record; merges it into its canonical document or passes it through. */
    public void add(DumStructuredSource source, DumStructuredRecord record) {
        Optional<String> canonicalId = canonicalKey.canonicalId(source, record);
        if (canonicalId.isEmpty()) {
            passthrough.add(new Entity(source, record));
            return;
        }
        String id = canonicalId.get();
        Entity existing = merged.get(id);
        if (existing == null) {
            merged.put(id, new Entity(source,
                    DumRecordMerge.rekey(record, id, source.getSourceName())));
        } else {
            merged.put(id, new Entity(existing.source(),
                    DumRecordMerge.merge(existing.record(), record, source.getSourceName())));
        }
    }

    /** Every resolved entity — merged canonical documents first, then unresolvable pass-throughs. */
    public List<Entity> entities() {
        List<Entity> all = new ArrayList<>(merged.values());
        all.addAll(passthrough);
        return all;
    }

    /** How many distinct entities the merge collapsed several source records into. */
    public int mergedCount() {
        return merged.size();
    }
}
