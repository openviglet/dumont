/*
 *
 * Copyright (C) 2016-2025 the original author or authors.
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
package com.viglet.dumont.connector.queue;

import static com.viglet.dumont.connector.constant.DumConnectorConstants.CONNECTOR_INDEXING_QUEUE;

import java.util.Enumeration;

import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DumConnectorQueue {
    private final JmsTemplate jmsTemplate;

    public DumConnectorQueue(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public int getQueueSize() {
        try {
            Integer size = jmsTemplate.browse(CONNECTOR_INDEXING_QUEUE, (session, browser) -> {
                Enumeration<?> messages = browser.getEnumeration();
                int total = 0;
                while (messages.hasMoreElements()) {
                    messages.nextElement();
                    total++;
                }
                return total;
            });
            return size != null ? size : 0;
        } catch (Exception e) {
            log.warn("Could not browse queue '{}': {}", CONNECTOR_INDEXING_QUEUE, e.getMessage());
            return 0;
        }
    }
}
