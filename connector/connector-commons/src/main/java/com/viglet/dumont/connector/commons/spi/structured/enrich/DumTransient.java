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
 * Marks an enricher contribution as a transient attribute rather than a field (roadmap
 * <strong>D97</strong>): {@code Map.of("locationUnresolved", DumTransient.of("Sao Paulo - SP"))}
 * lands in {@code DumStructuredRecord#transients()}, where a later enricher can read it and the
 * publish step never does. The key is not checked against the schema; {@code DumTransient.of(null)}
 * clears it.
 *
 * @param value the working value, or {@code null} to remove the key
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record DumTransient(Object value) {

    public static DumTransient of(Object value) {
        return new DumTransient(value);
    }
}
