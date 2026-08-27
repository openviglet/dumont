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

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.dumont.connector.plugin.aem.service.DumAemEventDispatcher;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * Adobe I/O Events webhook for AEM. Receives CloudEvents from Adobe Developer
 * Console and triggers Dumont path-level extraction, which the standalone
 * indexing pipeline forwards to Turing ES via {@code DumIndexingPlugin}.
 * <p>
 * GET handles the Adobe Developer Console verification challenge.
 * POST handles publish/modify/unpublish/delete events.
 * <p>
 * This is the low-latency route only. Event→path mapping lives in {@link DumAemEventDispatcher},
 * shared with the journal consumer that recovers whatever a push drops (roadmap
 * <strong>D62</strong>).
 * <p>
 * Existing crawl/index endpoints in {@link DumAemApi} are untouched.
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/aem/webhook")
@Tag(name = "AEM Webhook", description = "Adobe I/O Events webhook for AEM publish/modify notifications")
public class DumAemWebhookController {

    private static final String TYPE_FIELD = "type";
    private static final String RECIPIENT_CLIENT_ID_FIELD = "recipient_client_id";
    private static final String KEY_STATUS = "status";

    private final DumAemEventDispatcher eventDispatcher;
    private final String adobeIoClientId;

    public DumAemWebhookController(DumAemEventDispatcher eventDispatcher,
            @Value("${dumont.aem.webhook.adobe-io.client-id:}") String adobeIoClientId) {
        this.eventDispatcher = eventDispatcher;
        this.adobeIoClientId = adobeIoClientId;
        if (!StringUtils.hasText(adobeIoClientId)) {
            log.warn("AEM Webhook security is OPEN — set 'dumont.aem.webhook.adobe-io.client-id' "
                    + "to enforce Adobe I/O recipient validation.");
        }
    }

    /**
     * Adobe Developer Console verification handshake. Adobe sends a GET with a
     * {@code challenge} query parameter and expects the same value echoed back
     * as plain text with HTTP 200.
     */
    @GetMapping(value = "/{source}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> challenge(@PathVariable String source,
            @RequestParam(required = false) String challenge) {
        log.info("Adobe I/O challenge received | source={} | challenge={}", source, challenge);
        if (!StringUtils.hasText(challenge)) {
            return ResponseEntity.badRequest().body("Missing 'challenge' query parameter");
        }
        return ResponseEntity.ok(challenge);
    }

    /**
     * CloudEvents endpoint. Reads {@code type} and {@code data.resource_path}
     * from the payload and dispatches to the standalone indexing pipeline.
     */
    @PostMapping(value = "/{source}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> receive(@PathVariable String source,
            @RequestBody Map<String, Object> payload) {

        if (!isAuthorized(payload)) {
            log.warn("AEM Webhook rejected | source={} | reason=recipient_client_id mismatch", source);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(KEY_STATUS,"unauthorized"));
        }

        String eventType = asString(payload.get(TYPE_FIELD));

        log.info("AEM Webhook received | source={} | type={} | path={} | timestamp={}",
                source, eventType, DumAemEventDispatcher.resourcePath(payload), Instant.now());

        return switch (eventDispatcher.dispatch(source, payload)) {
            case IGNORED -> ResponseEntity.ok(Map.of(KEY_STATUS, "ignored"));
            case MISSING_RESOURCE_PATH ->
                ResponseEntity.badRequest().body(Map.of(KEY_STATUS, "missing-resource-path"));
            case ACCEPTED -> ResponseEntity.accepted().body(Map.of(KEY_STATUS, "accepted"));
        };
    }

    /**
     * Validates the payload's {@code recipient_client_id} against the configured
     * Adobe I/O Client ID. Returns {@code true} if the property is unset
     * (open / dev mode).
     */
    private boolean isAuthorized(Map<String, Object> payload) {
        if (!StringUtils.hasText(adobeIoClientId)) {
            return true;
        }
        return adobeIoClientId.equals(asString(payload.get(RECIPIENT_CLIENT_ID_FIELD)));
    }

    private static String asString(Object obj) {
        return obj == null ? null : obj.toString();
    }
}
