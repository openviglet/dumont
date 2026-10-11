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

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.viglet.dumont.connector.plugin.aem.conf.DumAemJournalProperties;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemPluginSystem;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemPluginSystemRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pulls one source's Adobe I/O journal and feeds it the shared event handler (roadmap
 * <strong>D62</strong>).
 *
 * <p>
 * The journal is the same ordered event stream the webhook receives by push, readable at the
 * consumer's own cadence behind a cursor. That cursor is what makes recovery exact rather than
 * best-effort: it is persisted only <em>after</em> every event on a page has been dispatched, so a
 * crash, a restart or a failing page replays from where it stopped instead of skipping ahead. The
 * webhook stays the low-latency route; this is the floor under it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */
@Slf4j
@Service
public class DumAemJournalService {

    private static final String CURSOR_SUFFIX = "journal-position";
    private static final String API_KEY_HEADER = "x-api-key";
    private static final String EVENTS = "events";
    private static final String EVENT = "event";
    private static final String BODY = "body";
    private static final String PAGE = "_page";
    private static final String LAST = "last";
    private static final String SINCE_PARAM = "since";
    private static final String LIMIT_PARAM = "limit";
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final DumAemJournalProperties properties;
    private final DumAemImsTokenService tokenService;
    private final DumAemEventDispatcher eventDispatcher;
    private final DumAemPluginSystemRepository systemRepository;
    private final RestClient restClient;

    /** The constructor Spring uses; with a second one present it must be named, or boot fails (D123). */
    @Autowired
    public DumAemJournalService(DumAemJournalProperties properties,
            DumAemImsTokenService tokenService, DumAemEventDispatcher eventDispatcher,
            DumAemPluginSystemRepository systemRepository) {
        this(properties, tokenService, eventDispatcher, systemRepository, RestClient.create());
    }

    /** Test seam: the same service against a client pointed at a stub journal. */
    DumAemJournalService(DumAemJournalProperties properties, DumAemImsTokenService tokenService,
            DumAemEventDispatcher eventDispatcher,
            DumAemPluginSystemRepository systemRepository, RestClient restClient) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.eventDispatcher = eventDispatcher;
        this.systemRepository = systemRepository;
        this.restClient = restClient;
    }

    /** The key this source's cursor is stored under, alongside the plugin's other config rows. */
    public static String cursorKey(String source) {
        return "%s/%s".formatted(source, CURSOR_SUFFIX);
    }

    /**
     * Drains up to {@code maxPagesPerPoll} pages for one source and returns how many events were
     * dispatched. Stops early on an empty page (caught up), a missing token, or a failed request —
     * in each case leaving the cursor where it was.
     */
    public long poll(String source, String journalUrl) {
        Optional<String> token = tokenService.accessToken();
        if (token.isEmpty()) {
            log.warn("AEM journal: no IMS token, skipping poll | source={}", source);
            return 0;
        }
        String cursor = cursor(source).orElse(null);
        long dispatched = 0;
        for (int page = 0; page < properties.getMaxPagesPerPoll(); page++) {
            JsonNode body = fetchPage(source, journalUrl, cursor, token.get());
            if (body == null) {
                break;
            }
            JsonNode events = body.get(EVENTS);
            if (events == null || !events.isArray() || events.isEmpty()) {
                log.debug("AEM journal: caught up | source={}", source);
                break;
            }
            dispatched += dispatchAll(source, events);
            String last = position(body);
            if (!StringUtils.hasText(last)) {
                // Without a position there is nothing safe to persist: the same page would be
                // re-read next poll, which is the harmless direction to fail in.
                log.warn("AEM journal: page carried no _page.last | source={}", source);
                break;
            }
            saveCursor(source, last);
            cursor = last;
        }
        if (dispatched > 0) {
            log.info("AEM journal: dispatched {} event(s) | source={}", dispatched, source);
        }
        return dispatched;
    }

    private long dispatchAll(String source, JsonNode events) {
        long accepted = 0;
        for (JsonNode entry : events) {
            Map<String, Object> payload = payloadOf(entry);
            if (payload == null) {
                log.warn("AEM journal: entry carried no event payload | source={}", source);
                continue;
            }
            if (eventDispatcher.dispatch(source, payload) == DumAemEventDispatcher.Outcome.ACCEPTED) {
                accepted++;
            }
        }
        return accepted;
    }

    private JsonNode fetchPage(String source, String journalUrl, String cursor, String token) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(journalUrl)
                .queryParam(LIMIT_PARAM, properties.getBatchSize());
        if (StringUtils.hasText(cursor)) {
            uri.queryParam(SINCE_PARAM, cursor);
        }
        try {
            return restClient.get()
                    .uri(uri.build(true).toUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(API_KEY_HEADER, properties.getClientId())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            // A rejected token is the common cause; drop it so the next poll re-exchanges.
            tokenService.invalidate();
            log.error("AEM journal: page request failed | source={} | {}", source, e.getMessage(), e);
            return null;
        }
    }

    /**
     * The CloudEvent inside a journal entry. Adobe wraps the event under {@code event}, and some
     * registrations nest the payload one level further under {@code body}; both are unwrapped, and an
     * entry that is already the event itself is accepted as-is.
     */
    private static Map<String, Object> payloadOf(JsonNode entry) {
        JsonNode event = entry.has(EVENT) ? entry.get(EVENT) : entry;
        if (event != null && event.has(BODY) && event.get(BODY).isObject()) {
            event = event.get(BODY);
        }
        if (event == null || !event.isObject()) {
            return null;
        }
        return MAPPER.convertValue(event,
                MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
    }

    private static String position(JsonNode body) {
        JsonNode page = body.get(PAGE);
        if (page != null && page.has(LAST)) {
            return page.get(LAST).asString(null);
        }
        return null;
    }

    /** The last position this source consumed, or empty on a first run. */
    public Optional<String> cursor(String source) {
        return systemRepository.findByConfig(cursorKey(source))
                .map(DumAemPluginSystem::getStringValue)
                .filter(StringUtils::hasText);
    }

    @Transactional
    public void saveCursor(String source, String position) {
        String key = cursorKey(source);
        DumAemPluginSystem row = systemRepository.findByConfig(key)
                .orElseGet(() -> new DumAemPluginSystem(key, position));
        row.setStringValue(position);
        systemRepository.save(row);
    }
}
