/*
 *
 * Copyright (C) 2016-2025 the original author or authors.
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

package com.viglet.dumont.connector.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.dumont.connector.domain.DumConnectorQueueStatus;
import com.viglet.dumont.connector.queue.DumConnectorQueue;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v2/connector/queue")
@Tag(name = "Connector Queue API", description = "Connector Queue API")
public class DumConnectorQueueApi {

    private final DumConnectorQueue queue;

    public DumConnectorQueueApi(DumConnectorQueue queue) {
        this.queue = queue;
    }

    @GetMapping("status")
    public ResponseEntity<DumConnectorQueueStatus> status() {
        return ResponseEntity.ok(new DumConnectorQueueStatus(queue.getQueueSize()));
    }
}
