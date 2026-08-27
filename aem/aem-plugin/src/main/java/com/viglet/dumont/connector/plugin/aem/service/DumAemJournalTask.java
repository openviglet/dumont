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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.plugin.aem.conf.DumAemJournalProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Polls every configured Adobe I/O journal on a fixed delay (roadmap <strong>D62</strong>).
 *
 * <p>
 * Only created when {@code dumont.aem.journal.enabled=true}, so an install that relies on the
 * webhook alone schedules nothing. A source whose poll throws is logged and the loop continues: one
 * unreachable journal must not stop the others, and its cursor is untouched so nothing is skipped.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "dumont.aem.journal.enabled", havingValue = "true")
public class DumAemJournalTask {

    private final DumAemJournalProperties properties;
    private final DumAemJournalService journalService;

    public DumAemJournalTask(DumAemJournalProperties properties,
            DumAemJournalService journalService) {
        this.properties = properties;
        this.journalService = journalService;
        if (!properties.isUsable()) {
            log.warn("AEM journal is enabled but not usable — set 'dumont.aem.journal.client-id', "
                    + "'client-secret' and at least one entry under 'sources'.");
        }
    }

    @Scheduled(fixedDelayString = "${dumont.aem.journal.poll-ms:60000}",
            initialDelayString = "${dumont.aem.journal.poll-ms:60000}")
    public void pollJournals() {
        if (!properties.isUsable()) {
            return;
        }
        for (Map.Entry<String, String> entry : properties.getSources().entrySet()) {
            String source = entry.getKey();
            try {
                journalService.poll(source, entry.getValue());
            } catch (Exception e) {
                log.error("AEM journal: poll failed | source={} | {}", source, e.getMessage(), e);
            }
        }
    }
}
