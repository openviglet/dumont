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

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

@Slf4j
public class DumAemUnixTimestamp extends StdDeserializer<Date> {
    public DumAemUnixTimestamp() {
        super(Date.class);
    }

    public DumAemUnixTimestamp(Class<?> vc) {
        super(vc);
    }

    @Override
    public Date deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
            throws JacksonException {

        String rawValue = jsonParser.getValueAsString();
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }

        String timestampStr = rawValue.trim();

        try {
            long timestamp = Long.parseLong(timestampStr);

            // If the timestamp is in seconds (10 digits), convert to milliseconds
            if (timestampStr.length() == 10) {
                timestamp *= 1000L;
            }

            return new Date(timestamp);
        } catch (NumberFormatException e) {
            log.error("Unable to deserialize timestamp: '{}'", rawValue, e);
            return null;
        }
    }
}