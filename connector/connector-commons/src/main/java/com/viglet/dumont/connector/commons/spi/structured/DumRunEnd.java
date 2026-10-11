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

package com.viglet.dumont.connector.commons.spi.structured;

/**
 * How a structured source's run ended, handed to {@link DumStructuredSource#onRunEnd} once the run
 * has finished (roadmap <strong>D115</strong>).
 *
 * @param partial whether the run stopped short of the whole source (aborted, interrupted, a
 *                discovery cut short, a failure wave, or an incremental run that did not reach the
 *                end), as the run record says (D96)
 * @param swept   whether the run-end sweep ran, so documents the run did not publish were deindexed
 * @param reason  why the run stopped short, or {@code null} when it did not
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public record DumRunEnd(boolean partial, boolean swept, String reason) {

    /** A run that saw the whole source and swept. */
    public static DumRunEnd complete() {
        return new DumRunEnd(false, true, null);
    }

    /** A run that stopped short for {@code reason}, finished without a sweep. */
    public static DumRunEnd stopped(String reason) {
        return new DumRunEnd(true, false, reason);
    }
}
