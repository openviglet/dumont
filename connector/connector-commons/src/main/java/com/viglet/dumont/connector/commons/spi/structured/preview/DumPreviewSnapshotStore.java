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

/**
 * Per-source persistence seam for the {@link DumPreviewSnapshot} baseline a dry-run preview
 * (roadmap <strong>D14</strong>) diffs against.
 *
 * <p>
 * A snapshot is saved at the end of a full index run and loaded by a later preview, so the preview
 * can report what would change versus the last indexed state. The seam keeps the structured-source
 * commons free of any storage choice — the app supplies the implementation (an in-memory singleton
 * by default; a persisted store later).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public interface DumPreviewSnapshotStore {

    /** The last snapshot captured for the source, or empty when it has never been fully indexed. */
    Optional<DumPreviewSnapshot> load(String sourceName);

    /** Replace the baseline for the source with a snapshot of its latest full index run. */
    void save(String sourceName, DumPreviewSnapshot snapshot);
}
