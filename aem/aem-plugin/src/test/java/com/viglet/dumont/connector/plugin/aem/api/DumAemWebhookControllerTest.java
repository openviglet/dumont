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

package com.viglet.dumont.connector.plugin.aem.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.viglet.dumont.connector.plugin.aem.DumAemPluginProcess;

class DumAemWebhookControllerTest {

    private final DumAemPluginProcess process = org.mockito.Mockito.mock(DumAemPluginProcess.class);

    private DumAemWebhookController open() {
        return new DumAemWebhookController(process, "");
    }

    @Test
    void challengeEchoesValueWhenPresent() {
        ResponseEntity<String> response = open().challenge("wknd", "abc123");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("abc123");
    }

    @Test
    void challengeRejectsMissingValue() {
        assertThat(open().challenge("wknd", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void receivePublishedEventDispatchesToPipeline() {
        Map<String, Object> payload = Map.of(
                "type", "aem.sites.page.published",
                "data", Map.of("resource_path", "/content/wknd/en"));

        ResponseEntity<Map<String, String>> response = open().receive("wknd", payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsEntry("status", "accepted");
        verify(process).sentToIndexStandaloneAsync(eq("wknd"), any(DumAemPathList.class));
    }

    @Test
    void receiveIgnoresUnsupportedEventType() {
        Map<String, Object> payload = Map.of("type", "aem.sites.page.created",
                "data", Map.of("resource_path", "/content/wknd/en"));

        ResponseEntity<Map<String, String>> response = open().receive("wknd", payload);

        assertThat(response.getBody()).containsEntry("status", "ignored");
        verify(process, never()).sentToIndexStandaloneAsync(any(), any());
    }

    @Test
    void receiveRejectsMissingResourcePath() {
        Map<String, Object> payload = Map.of("type", "aem.sites.page.modified");

        ResponseEntity<Map<String, String>> response = open().receive("wknd", payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", "missing-resource-path");
    }

    @Test
    void receiveRejectsUnauthorizedRecipient() {
        DumAemWebhookController secured = new DumAemWebhookController(process, "expected-client");
        Map<String, Object> payload = Map.of("type", "aem.sites.page.published",
                "recipient_client_id", "other-client",
                "data", Map.of("resource_path", "/content/wknd/en"));

        ResponseEntity<Map<String, String>> response = secured.receive("wknd", payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(process, never()).sentToIndexStandaloneAsync(any(), any());
    }

    @Test
    void receiveAuthorizesMatchingRecipient() {
        DumAemWebhookController secured = new DumAemWebhookController(process, "expected-client");
        Map<String, Object> payload = Map.of("type", "aem.sites.page.unpublished",
                "recipient_client_id", "expected-client",
                "data", Map.of("resource_path", "/content/wknd/en"));

        ResponseEntity<Map<String, String>> response = secured.receive("wknd", payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void receiveMapsDeletedEventType() {
        Map<String, Object> payload = Map.of("type", "aem.sites.page.deleted",
                "data", Map.of("resource_path", "/content/wknd/en"));

        assertThat(open().receive("wknd", payload).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
    }
}
