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

package com.viglet.dumont.connector.api;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.dumont.connector.service.DumIndexOrphanService;
import com.viglet.dumont.connector.service.DumIndexOrphanService.ApplyResult;
import com.viglet.dumont.connector.service.DumIndexOrphanService.OrphanReport;
import com.viglet.dumont.connector.service.DumIndexOrphanService.Outcome;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The review the index audit's orphans wait in (roadmap <strong>D68</strong>): read the evidence,
 * then apply it or throw it away. Nothing here deletes anything the operator did not name.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */
@RestController
@RequestMapping("/api/v2/connector/orphans")
@Tag(name = "Connector API", description = "Connector API")
public class DumConnectorOrphanApi {

    private static final String STATUS_KEY = "status";
    private final DumIndexOrphanService orphanService;

    public DumConnectorOrphanApi(DumIndexOrphanService orphanService) {
        this.orphanService = orphanService;
    }

    /** Every orphan report, newest evidence first; one source's when {@code source} is given. */
    @GetMapping
    public ResponseEntity<List<OrphanReport>> reports(
            @RequestParam(required = false) String source) {
        return ResponseEntity.ok(orphanService.reports(source));
    }

    @GetMapping("{id}")
    public ResponseEntity<OrphanReport> report(@PathVariable int id) {
        return orphanService.report(id).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Deindexes a pending report's ids through the connector's own delete path.
     *
     * <p>
     * A report already decided answers 409, and one whose evidence aged past
     * {@code dumont.audit.index.orphanTtl} answers 410 — applying a stale set to a source
     * reindexed since would delete live content, so it must be re-established by a fresh audit.
     */
    @PostMapping("{id}/apply")
    public ResponseEntity<Map<String, Object>> apply(@PathVariable int id) {
        ApplyResult result = orphanService.apply(id);
        Map<String, Object> body = status(result.outcome());
        body.put("deindexed", result.deindexed());
        return ResponseEntity.status(httpStatus(result.outcome())).body(body);
    }

    /** Closes a pending report without deleting anything. */
    @PostMapping("{id}/discard")
    public ResponseEntity<Map<String, Object>> discard(@PathVariable int id) {
        Outcome outcome = orphanService.discard(id);
        return ResponseEntity.status(httpStatus(outcome)).body(status(outcome));
    }

    private static Map<String, Object> status(Outcome outcome) {
        Map<String, Object> body = new HashMap<>();
        body.put(STATUS_KEY, outcome.name().toLowerCase(Locale.ROOT));
        return body;
    }

    private static HttpStatus httpStatus(Outcome outcome) {
        return switch (outcome) {
            case APPLIED, DISCARDED -> HttpStatus.OK;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NOT_PENDING -> HttpStatus.CONFLICT;
            case EXPIRED -> HttpStatus.GONE;
        };
    }
}
