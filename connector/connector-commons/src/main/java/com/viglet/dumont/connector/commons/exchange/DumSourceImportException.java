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

/**
 * A source import refused as a whole (roadmap <strong>DU9</strong>): an unreadable file, an unknown
 * mode, or a replacing import whose file holds a name that cannot be saved. Unchecked, so it rolls the
 * import's transaction back; the source APIs answer it with 400 and its message.
 */
public class DumSourceImportException extends RuntimeException {

    public DumSourceImportException(String message) {
        super(message);
    }

    public DumSourceImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
