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

package com.viglet.dumont.connector.commons.plugin;

import java.io.Serial;

/**
 * Thrown to a connector that offers its run another document after the search backend stopped
 * taking them (roadmap <strong>D343</strong>), so the connector stops reading its source. The
 * message is the reason the run record carries, e.g. "Viglet Turing unreachable at ...".
 */
public class DumRunStoppedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DumRunStoppedException(String reason) {
        super(reason);
    }
}
