/*
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

/**
 * The search backend did not accept a batch (roadmap <strong>D138</strong>): an unreachable server,
 * a rejected request. Thrown by {@link DumIndexingPlugin#index} so the queue can mark the batch's
 * documents for resending instead of leaving them recorded as indexed.
 */
public class DumIndexingDeliveryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DumIndexingDeliveryException(String message) {
        super(message);
    }

    public DumIndexingDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
