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

package com.viglet.dumont.connector.plugin.aem.conf;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * The Adobe I/O journal consumer's configuration (roadmap <strong>D62</strong>).
 *
 * <p>
 * Journalling is enabled automatically on every Adobe Developer Console event registration, so the
 * only thing an operator supplies is the journal URL of a registration and the server-to-server
 * credentials to read it. Configured globally, keyed by source name, mirroring how the webhook takes
 * its Adobe I/O client id from a property and its source from the request path.
 *
 * <pre>{@code
 * dumont:
 *   aem:
 *     journal:
 *       enabled: true
 *       poll-ms: 60000
 *       client-id: <adobe io client id>
 *       client-secret: <adobe io client secret>
 *       sources:
 *         wknd: https://events-va6.adobe.io/events/organizations/1/integrations/2/3
 * }</pre>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */
@Getter
@Setter
@Component
@ConfigurationProperties("dumont.aem.journal")
public class DumAemJournalProperties {

    /** Master switch. Off by default: the webhook alone is the previous behaviour. */
    private boolean enabled = false;

    /** Delay between the end of one poll and the start of the next, in milliseconds. */
    private long pollMs = 60_000L;

    /** Events requested per journal page. Adobe caps this; a page is followed by the next link. */
    private int batchSize = 100;

    /** Pages followed in one poll, so a large backlog is drained across polls, not in one burst. */
    private int maxPagesPerPoll = 20;

    /** Adobe I/O (Developer Console) credentials for the server-to-server token exchange. */
    private String clientId;
    private String clientSecret;

    /** The IMS token endpoint. Overridable for a non-NA1 org or a test double. */
    private String imsUrl = "https://ims-na1.adobelogin.com/ims/token/v3";

    /** Scopes requested for the token. Adobe documents these per registration. */
    private String scopes = "openid,AdobeID,adobeio_api,event_receiver_api";

    /** Journal URL per Dumont source name — the source the pulled events are dispatched to. */
    private Map<String, String> sources = new LinkedHashMap<>();

    /**
     * Whether a poll can actually run: switched on, credentialed, and with at least one source
     * mapped. Checked before scheduling work so a half-configured install logs once instead of
     * failing every interval.
     */
    public boolean isUsable() {
        return enabled && StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret)
                && !sources.isEmpty();
    }
}
