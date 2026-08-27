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

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;
import com.viglet.dumont.connector.plugin.aem.DumAemPluginProcess;
import com.viglet.dumont.connector.plugin.aem.api.DumAemAttributeIndex;
import com.viglet.dumont.connector.plugin.aem.api.DumAemPathList;

import lombok.extern.slf4j.Slf4j;

/**
 * Turns one Adobe I/O event into one path-level indexing request (roadmap <strong>D62</strong>).
 *
 * <p>
 * Adobe I/O delivers the same events two ways: pushed to a webhook, and pulled from a journal. This
 * is the single handler both feed, so the journal is a recovery path for the webhook rather than a
 * second implementation of it — a mapping fixed in one place is fixed for both.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */
@Slf4j
@Service
public class DumAemEventDispatcher {

    private static final String EVENT_PUBLISHED_SUFFIX = ".published";
    private static final String EVENT_MODIFIED_SUFFIX = ".modified";
    private static final String EVENT_UNPUBLISHED_SUFFIX = ".unpublished";
    private static final String EVENT_DELETED_SUFFIX = ".deleted";
    private static final String DATA_RESOURCE_PATH = "resource_path";
    private static final String DATA_FIELD = "data";
    private static final String TYPE_FIELD = "type";

    /** What the dispatcher did with an event, so a caller can answer or count it. */
    public enum Outcome {
        /** Queued for indexing. */
        ACCEPTED,
        /** A well-formed event of a type this connector does not act on. */
        IGNORED,
        /** An actionable type with no {@code data.resource_path} — the event is unusable. */
        MISSING_RESOURCE_PATH
    }

    private final DumAemPluginProcess dumAemPluginProcess;

    public DumAemEventDispatcher(DumAemPluginProcess dumAemPluginProcess) {
        this.dumAemPluginProcess = dumAemPluginProcess;
    }

    /**
     * Reads {@code type} and {@code data.resource_path} out of a CloudEvent payload and dispatches
     * it. Accepts the payload shape both transports carry, so neither has to unpack it.
     */
    public Outcome dispatch(String source, Map<String, Object> payload) {
        return dispatch(source, asString(payload.get(TYPE_FIELD)), resourcePath(payload));
    }

    /**
     * Dispatches an already-unpacked event. An unsupported type is {@link Outcome#IGNORED} rather
     * than an error: Adobe I/O registrations routinely carry types a given connector does not act on.
     */
    public Outcome dispatch(String source, String eventType, String resourcePath) {
        DumAemEvent event = mapEvent(eventType);
        if (event == null) {
            log.debug("AEM event ignored | source={} | unsupported type={}", source, eventType);
            return Outcome.IGNORED;
        }
        if (!StringUtils.hasText(resourcePath)) {
            log.warn("AEM event missing data.resource_path | source={} | type={}", source, eventType);
            return Outcome.MISSING_RESOURCE_PATH;
        }
        dumAemPluginProcess.sentToIndexStandaloneAsync(source, DumAemPathList.builder()
                .attribute(DumAemAttributeIndex.ID)
                .paths(List.of(resourcePath))
                .event(event)
                .recursive(Boolean.FALSE)
                .build());
        return Outcome.ACCEPTED;
    }

    /** The {@code data.resource_path} an AEM CloudEvent carries, or null when absent. */
    public static String resourcePath(Map<String, Object> payload) {
        if (payload != null && payload.get(DATA_FIELD) instanceof Map<?, ?> data
                && data.get(DATA_RESOURCE_PATH) instanceof String path) {
            return path;
        }
        return null;
    }

    private static DumAemEvent mapEvent(String type) {
        if (type == null) {
            return null;
        }
        if (type.endsWith(EVENT_PUBLISHED_SUFFIX)) {
            return DumAemEvent.PUBLISHING;
        }
        if (type.endsWith(EVENT_MODIFIED_SUFFIX)) {
            return DumAemEvent.INDEXING;
        }
        if (type.endsWith(EVENT_UNPUBLISHED_SUFFIX)) {
            return DumAemEvent.UNPUBLISHING;
        }
        if (type.endsWith(EVENT_DELETED_SUFFIX)) {
            return DumAemEvent.DEINDEXING;
        }
        return null;
    }

    private static String asString(Object obj) {
        return obj == null ? null : obj.toString();
    }
}
