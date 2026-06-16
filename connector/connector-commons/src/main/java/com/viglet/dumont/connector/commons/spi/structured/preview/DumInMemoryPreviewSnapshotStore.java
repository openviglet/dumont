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

package com.viglet.dumont.connector.commons.spi.structured.preview;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Process-local {@link DumPreviewSnapshotStore} — the default baseline store for dry-run previews
 * (roadmap <strong>D14</strong>). A single instance shared across requests gives a useful baseline
 * for the lifetime of the running connector (re-config of a live source diffs against its last
 * index); it intentionally does not survive a restart, which the preview's first-onboarding path
 * already handles gracefully (an empty baseline reads as "all added").
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public final class DumInMemoryPreviewSnapshotStore implements DumPreviewSnapshotStore {

    private final ConcurrentMap<String, DumPreviewSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public Optional<DumPreviewSnapshot> load(String sourceName) {
        return Optional.ofNullable(snapshots.get(sourceName));
    }

    @Override
    public void save(String sourceName, DumPreviewSnapshot snapshot) {
        if (sourceName != null && snapshot != null) {
            snapshots.put(sourceName, snapshot);
        }
    }
}
