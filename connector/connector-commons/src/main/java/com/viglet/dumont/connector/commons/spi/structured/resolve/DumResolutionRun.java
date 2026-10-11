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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;

/**
 * One source's run against the {@link DumResolutionIndex} (roadmap <strong>D109</strong>): what each
 * of its records publishes as, and what it must still publish or let go when the run ends.
 *
 * <p>
 * {@link #resolve} files a record under its canonical id and answers the canonical document: what the
 * {@link DumSurvivorship} makes of every live member, handed over in the order they were first filed,
 * so the outcome is the same in every run of every source. A record with no canonical id is answered
 * unchanged, on its own id.
 *
 * <p>
 * {@link #closeComplete} is for a run that saw the whole source: the members it did not see are held,
 * the canonical documents the source holds only through held members are answered for publishing
 * while another source's member is live (so this source's sweep does not delete them), and the
 * canonical ids left with no live member are dropped.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public final class DumResolutionRun {

    /** What an incremental run's expired ids become: ids to deindex, documents to publish again. */
    public record Expiry(List<String> deindex, List<DumStructuredRecord> republish) {
    }

    private record Seen(String canonicalId, String recordId) {
    }

    private final DumCanonicalKey canonicalKey;
    private final DumResolutionIndex index;
    private final DumStructuredSource source;
    private final DumSurvivorship survivorship;
    private final Set<Seen> seen = new HashSet<>();
    private final Set<String> published = new HashSet<>();

    /** A run whose documents follow {@link DumSurvivorship#fieldMerge()}. */
    public DumResolutionRun(DumCanonicalKey canonicalKey, DumResolutionIndex index,
            DumStructuredSource source) {
        this(canonicalKey, index, source, DumSurvivorship.fieldMerge());
    }

    /** A run whose documents follow {@code survivorship} (D110). */
    public DumResolutionRun(DumCanonicalKey canonicalKey, DumResolutionIndex index,
            DumStructuredSource source, DumSurvivorship survivorship) {
        this.canonicalKey = canonicalKey;
        this.index = index;
        this.source = source;
        this.survivorship = survivorship;
    }

    /** File {@code rec} and answer the document it publishes as. */
    public DumStructuredRecord resolve(DumStructuredRecord rec) {
        Optional<String> canonicalId = canonicalKey.canonicalId(source, rec);
        if (canonicalId.isEmpty()) {
            return rec;
        }
        String id = canonicalId.get();
        index.put(id, source.getSourceName(), rec.id(), rec);
        seen.add(new Seen(id, rec.id()));
        published.add(id);
        return merged(id).orElse(rec);
    }

    /**
     * The document ids of records the source reports unchanged (D104): a record filed under a
     * canonical id is kept alive under that id, and counts as seen.
     */
    public List<String> documentIds(Collection<String> recordIds) {
        List<String> ids = new ArrayList<>();
        for (String recordId : recordIds) {
            Optional<String> canonicalId = index.canonicalIdOf(source.getSourceName(), recordId);
            if (canonicalId.isPresent()) {
                seen.add(new Seen(canonicalId.get(), recordId));
                published.add(canonicalId.get());
                ids.add(canonicalId.get());
            } else {
                ids.add(recordId);
            }
        }
        return ids;
    }

    /**
     * End a run that saw the whole source: hold what it did not see, and answer the canonical
     * documents it must publish only to keep them, because another source still has a live member.
     */
    public List<DumStructuredRecord> closeComplete() {
        Set<String> canonicalIds = new LinkedHashSet<>();
        for (DumResolutionIndex.Member member : index.bySource(source.getSourceName())) {
            canonicalIds.add(member.canonicalId());
            if (member.live() && !seen.contains(new Seen(member.canonicalId(), member.recordId()))) {
                index.hold(member.canonicalId(), member.source(), member.recordId());
            }
        }
        return keepOrDrop(canonicalIds, new ArrayList<>());
    }

    /**
     * An incremental run's expired ids (D92): a record filed under a canonical id is held, and its
     * document is deindexed only when no member of it is live; the rest are deindexed as they are.
     */
    public Expiry expire(Collection<String> expiredIds) {
        List<String> deindex = new ArrayList<>();
        Set<String> canonicalIds = new LinkedHashSet<>();
        for (String recordId : expiredIds) {
            Optional<String> canonicalId = index.canonicalIdOf(source.getSourceName(), recordId);
            if (canonicalId.isPresent()) {
                index.hold(canonicalId.get(), source.getSourceName(), recordId);
                canonicalIds.add(canonicalId.get());
            } else {
                deindex.add(recordId);
            }
        }
        List<DumStructuredRecord> republish = keepOrDrop(canonicalIds, deindex);
        return new Expiry(deindex, republish);
    }

    /**
     * The canonical document of {@code canonicalId}, as the survivorship rule makes it from the live
     * members, if any is live.
     */
    public Optional<DumStructuredRecord> merged(String canonicalId) {
        List<DumResolutionIndex.Member> live = index.members(canonicalId).stream()
                .filter(DumResolutionIndex.Member::live).toList();
        return live.isEmpty() ? Optional.empty() : Optional.of(survivorship.survive(canonicalId, live));
    }

    /**
     * For each canonical id this run did not publish: its document while a member is live, else the
     * id is dropped (and added to {@code gone}).
     */
    private List<DumStructuredRecord> keepOrDrop(Collection<String> canonicalIds, List<String> gone) {
        List<DumStructuredRecord> keep = new ArrayList<>();
        for (String canonicalId : canonicalIds) {
            Optional<DumStructuredRecord> document = merged(canonicalId);
            if (document.isEmpty()) {
                index.drop(canonicalId);
                gone.add(canonicalId);
            } else if (published.add(canonicalId)) {
                keep.add(document.get());
            }
        }
        return keep;
    }
}
