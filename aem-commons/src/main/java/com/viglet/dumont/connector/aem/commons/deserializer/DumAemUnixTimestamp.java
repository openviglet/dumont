/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.aem.commons.deserializer;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

public class DumAemUnixTimestamp extends StdDeserializer<Date> {
    private static final Logger log = LoggerFactory.getLogger(DumAemUnixTimestamp.class);

    public DumAemUnixTimestamp() {
        this(null);
    }

    public DumAemUnixTimestamp(Class<?> vc) {
        super(vc);
    }

    @Override
    public Date deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
            throws JacksonException {

        String timestamp = jsonParser.getValueAsString();

        if (timestamp == null || timestamp.trim().isEmpty()) {
            return null;
        }

        timestamp = timestamp.trim();

        try {
            while (timestamp.length() < 10) {
                timestamp += "0";
            }

            long timeMillis;
            if (timestamp.length() == 10) {
                timeMillis = Long.parseLong(timestamp) * 1000L;
            } else {
                timeMillis = Long.parseLong(timestamp);
            }

            return new Date(timeMillis);
        } catch (NumberFormatException e) {
            log.error("Unable to deserialize timestamp: {}", timestamp, e);
            return null;
        }
    }
}