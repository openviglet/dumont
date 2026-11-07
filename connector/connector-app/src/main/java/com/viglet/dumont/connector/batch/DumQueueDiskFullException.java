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

package com.viglet.dumont.connector.batch;

import java.io.Serial;

/** A batch the embedded queue would block on, refused before it is sent (roadmap D341). */
public class DumQueueDiskFullException extends IllegalStateException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DumQueueDiskFullException(String message) {
        super(message);
    }
}
