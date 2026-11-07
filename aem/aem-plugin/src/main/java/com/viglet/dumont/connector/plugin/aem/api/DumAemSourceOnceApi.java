/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.aem.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.dumont.connector.plugin.aem.service.DumAemSourceService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSourceService.OnceProgress;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * How far an AEM source's once pattern has gone (roadmap <strong>D344</strong>): the full runs that
 * indexed its paths so far, of the {@code onceTimes} it asks for, and a way to start the count
 * again.
 */
@RestController
@RequestMapping("/api/v2/aem/source/{id}/once")
@Tag(name = "AEM Source", description = "AEM Source")
public class DumAemSourceOnceApi {

    private final DumAemSourceService sourceService;

    public DumAemSourceOnceApi(DumAemSourceService sourceService) {
        this.sourceService = sourceService;
    }

    @Operation(summary = "Full runs that indexed the once pattern's paths, of onceTimes")
    @GetMapping
    public ResponseEntity<OnceProgress> progress(@PathVariable String id) {
        return sourceService.getDumAemSourceById(id)
                .map(source -> ResponseEntity.ok(sourceService.onceProgress(source)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Start the once count again")
    @DeleteMapping
    public ResponseEntity<OnceProgress> reset(@PathVariable String id) {
        return sourceService.getDumAemSourceById(id).map(source -> {
            sourceService.resetOnce(source);
            return ResponseEntity.ok(sourceService.onceProgress(source));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
