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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;

/**
 * The {@link DumResolutionIndex} kept in memory for the life of the plugin (roadmap
 * <strong>D109</strong>): runs of different sources, and {@code indexAll} as well as {@code crawl},
 * resolve against each other until the process restarts.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public final class DumInMemoryResolutionIndex implements DumResolutionIndex {

    private record Key(String canonicalId, String source, String recordId) {
    }

    private final Map<Key, Member> members = new LinkedHashMap<>();
    private long sequence;

    @Override
    public synchronized void put(String canonicalId, String source, String recordId,
            DumStructuredRecord rec) {
        for (Map.Entry<Key, Member> entry : members.entrySet()) {
            Key key = entry.getKey();
            Member member = entry.getValue();
            if (key.source().equals(source) && key.recordId().equals(recordId)
                    && !key.canonicalId().equals(canonicalId) && member.live()) {
                entry.setValue(withLive(member, false));
            }
        }
        Key key = new Key(canonicalId, source, recordId);
        Member existing = members.get(key);
        long order = existing != null ? existing.order() : sequence++;
        members.put(key, new Member(canonicalId, source, recordId, rec, true, order));
    }

    @Override
    public synchronized List<Member> members(String canonicalId) {
        return members.values().stream().filter(member -> member.canonicalId().equals(canonicalId))
                .sorted(Comparator.comparingLong(Member::order)).toList();
    }

    @Override
    public synchronized List<Member> bySource(String source) {
        return members.values().stream().filter(member -> member.source().equals(source)).toList();
    }

    @Override
    public synchronized Optional<String> canonicalIdOf(String source, String recordId) {
        return members.values().stream()
                .filter(member -> member.live() && member.source().equals(source)
                        && member.recordId().equals(recordId))
                .map(Member::canonicalId).findFirst();
    }

    @Override
    public synchronized void hold(String canonicalId, String source, String recordId) {
        members.computeIfPresent(new Key(canonicalId, source, recordId),
                (key, member) -> withLive(member, false));
    }

    @Override
    public synchronized void drop(String canonicalId) {
        members.keySet().removeIf(key -> key.canonicalId().equals(canonicalId));
    }

    private static Member withLive(Member member, boolean live) {
        return new Member(member.canonicalId(), member.source(), member.recordId(), member.rec(), live,
                member.order());
    }
}
