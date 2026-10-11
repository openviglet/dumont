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

package com.viglet.dumont.connector.service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingStatsModel;

import lombok.extern.slf4j.Slf4j;

/**
 * Tells an operator who is not watching the console that a source's run raised a warning (roadmap
 * <strong>D130</strong>), by posting the run to {@code dumont.alerts.webhook-url}. Unset, the
 * default, it sends nothing.
 *
 * <p>
 * It is called only when a source <em>starts</em> warning, not on every warned run, so a partner
 * that stays down is one message, not one per run. Its own client, not the shared Turing one: the
 * webhook is a third party and must never receive Turing's {@code Key}. A failed or slow delivery is
 * logged and dropped; it never fails the run it reports.
 */
@Slf4j
@Component
public class DumRunWarningNotifier {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final String webhookUrl;
    private final RestClient client;

    @Autowired
    public DumRunWarningNotifier(@Value("${dumont.alerts.webhook-url:}") String webhookUrl) {
        this(webhookUrl, RestClient.builder().requestFactory(timeouts()).build());
    }

    DumRunWarningNotifier(String webhookUrl, RestClient client) {
        this.webhookUrl = webhookUrl;
        this.client = client;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        return factory;
    }

    public boolean enabled() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }

    /** Posts {@code run}, the first warned run of its source, to the webhook. */
    public void runStartedWarning(DumConnectorIndexingStatsModel run) {
        if (!enabled()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", "run.warned");
        body.put("source", run.getSource());
        body.put("provider", run.getProvider());
        body.put("operationType", run.getOperationType());
        body.put("endTime", run.getEndTime());
        body.put("documentCount", run.getDocumentCount());
        body.put("partial", run.isPartial());
        body.put("warnings", run.getWarnings());
        try {
            client.post().uri(webhookUrl).contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                    .toBodilessEntity();
            log.info("Sent the warning on source '{}' to the alert webhook", run.getSource());
        } catch (RestClientException e) {
            log.warn("Alert webhook did not take the warning on source '{}': {}", run.getSource(),
                    e.getMessage());
        }
    }
}
