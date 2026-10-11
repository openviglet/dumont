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

package com.viglet.dumont.connector.commons.spi.structured.enrich;

/**
 * An enricher contribution that removes a field (roadmap <strong>D98</strong>):
 * {@code Map.of("city", DumClear.field())} drops the city the source supplied, together with
 * its provenance. Canonicalizing sometimes means removing a wrong value, such as a city that is
 * not in the stated state, and leaving the field absent is the grounding contract's answer.
 *
 * <p>
 * A blank contribution still changes nothing: only this explicit marker clears a field, so an
 * enricher that merely has nothing to say can never erase what the source sent by accident.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum DumClear {

    /** The single marker. */
    FIELD;

    /** The marker, read as {@code DumClear.field()} at the call site. */
    public static DumClear field() {
        return FIELD;
    }
}
