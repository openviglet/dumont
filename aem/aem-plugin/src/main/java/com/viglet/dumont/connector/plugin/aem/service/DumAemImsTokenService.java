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

package com.viglet.dumont.connector.plugin.aem.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.plugin.aem.conf.DumAemJournalProperties;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * Adobe IMS access token for the journal consumer (roadmap <strong>D62</strong>).
 *
 * <p>
 * A server-to-server client-credentials exchange, cached until shortly before it expires so a poll
 * every minute does not mean a token request every minute. Refreshed early by
 * {@link #EXPIRY_MARGIN} rather than on the first 401, because a token that expires mid-poll would
 * otherwise cost the page it was fetching.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */
@Slf4j
@Service
public class DumAemImsTokenService {

    /** Refreshed this long before the reported expiry. */
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(5);
    private static final String ACCESS_TOKEN = "access_token";
    private static final String EXPIRES_IN = "expires_in";

    private final DumAemJournalProperties properties;
    private final RestClient restClient;

    private String cachedToken;
    private Instant cachedUntil = Instant.EPOCH;

    /** The constructor Spring uses; with a second one present it must be named, or boot fails (D123). */
    @Autowired
    public DumAemImsTokenService(DumAemJournalProperties properties) {
        this(properties, RestClient.create());
    }

    /** Test seam: the same service against a client pointed at a stub IMS. */
    DumAemImsTokenService(DumAemJournalProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    /**
     * A valid access token, from cache when it has margin left. Empty when the exchange fails — the
     * caller skips the poll and retries on the next interval rather than advancing any cursor.
     */
    public synchronized Optional<String> accessToken() {
        if (cachedToken != null && Instant.now().isBefore(cachedUntil)) {
            return Optional.of(cachedToken);
        }
        return requestToken();
    }

    /** Drops the cached token, so the next {@link #accessToken()} re-exchanges. */
    public synchronized void invalidate() {
        cachedToken = null;
        cachedUntil = Instant.EPOCH;
    }

    private Optional<String> requestToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("scope", properties.getScopes());
        try {
            JsonNode body = restClient.post()
                    .uri(properties.getImsUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            return cache(body);
        } catch (Exception e) {
            log.error("AEM journal: IMS token exchange failed: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    private Optional<String> cache(JsonNode body) {
        String token = body != null && body.has(ACCESS_TOKEN)
                ? body.get(ACCESS_TOKEN).asString(null)
                : null;
        if (!StringUtils.hasText(token)) {
            log.error("AEM journal: IMS token response carried no access_token");
            return Optional.empty();
        }
        long expiresIn = body.has(EXPIRES_IN) ? body.get(EXPIRES_IN).asLong(0L) : 0L;
        Duration lifetime = Duration.ofSeconds(expiresIn).minus(EXPIRY_MARGIN);
        cachedToken = token;
        // A token whose reported lifetime is shorter than the margin is used once, not cached.
        cachedUntil = lifetime.isPositive() ? Instant.now().plus(lifetime) : Instant.EPOCH;
        log.debug("AEM journal: IMS token obtained, cached for {}s", Math.max(0, lifetime.toSeconds()));
        return Optional.of(token);
    }
}
