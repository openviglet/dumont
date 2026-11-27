/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.dumont.connector.commons.exchange;

import java.util.Locale;

/**
 * How a source import treats the sources an instance already has (roadmap <strong>DU9</strong>).
 *
 * <ul>
 * <li>{@link #ADD}: the default and the only mode before DU9. A source whose id or name already
 * exists is skipped, so an import never changes or removes anything.</li>
 * <li>{@link #REPLACE}: the file is the whole set for that connector type. Every existing source of
 * the type is deleted and the file's sources are created, in one transaction, so a file that fails
 * halfway changes nothing. Sources keep their names, and the indexing history, rules and stats are
 * keyed by name, so they stay attached; ids are regenerated.</li>
 * <li>{@link #MERGE} (roadmap <strong>DU14</strong>): the file updates the sources it names and
 * leaves the others alone. A source whose name matches one in the file is replaced by the file's
 * version (deleted and recreated under the same name, as {@link #REPLACE} does), a new name is
 * created, and an existing source the file does not name is kept. Same transaction and refusal
 * rules as {@link #REPLACE}.</li>
 * </ul>
 */
public enum DumSourceImportMode {
    ADD,
    REPLACE,
    MERGE;

    /**
     * The mode a request parameter names, case-insensitively; null or blank is {@link #ADD}.
     *
     * @throws DumSourceImportException for any other value, so a typo never imports in a mode
     *                                  nobody asked for
     */
    public static DumSourceImportMode of(String value) {
        if (value == null || value.isBlank()) {
            return ADD;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DumSourceImportException("Unknown import mode '" + value + "': use add, replace or merge.");
        }
    }
}
