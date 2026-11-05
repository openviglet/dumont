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

package com.viglet.dumont.connector.commons.spi.structured.oaipmh;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link DumOaiPmhState} held in memory (roadmap <strong>D315</strong>): enough for a test, and
 * lost on restart otherwise, which only means the next run harvests in full.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumInMemoryOaiPmhState implements DumOaiPmhState {

    private final AtomicReference<String> watermark = new AtomicReference<>();

    @Override
    public Optional<String> watermark() {
        return Optional.ofNullable(watermark.get());
    }

    @Override
    public void watermark(String responseDate) {
        watermark.set(responseDate);
    }
}
