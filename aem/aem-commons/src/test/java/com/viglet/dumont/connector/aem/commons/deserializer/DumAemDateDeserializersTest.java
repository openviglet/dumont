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

package com.viglet.dumont.connector.aem.commons.deserializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

class DumAemDateDeserializersTest {

    private static JsonMapper mapperWith(Object deserializer) {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Date.class, (tools.jackson.databind.ValueDeserializer<Date>) deserializer);
        return JsonMapper.builder().addModule(module).build();
    }

    private static Date parse(Object deserializer, String json) {
        return mapperWith(deserializer).readValue('"' + json + '"', Date.class);
    }

    @Test
    void datesParsesIsoOffsetFormat() {
        Date date = parse(new DumAemDates(), "2024-01-01T00:00:00Z");
        assertNotNull(date);
    }

    @Test
    void datesParsesMillisFormat() {
        assertNotNull(parse(new DumAemDates(), "2024-01-01T12:30:45.123Z"));
    }

    @Test
    void datesReturnsNullForBlankOrUnparseable() {
        assertNull(parse(new DumAemDates(), ""));
        assertNull(parse(new DumAemDates(), "not-a-date"));
    }

    @Test
    void unixTimestampParsesSeconds(){
        Date date = parse(new DumAemUnixTimestamp(), "1700000000");
        assertEquals(1_700_000_000_000L, date.getTime());
    }

    @Test
    void unixTimestampParsesMillis() {
        Date date = parse(new DumAemUnixTimestamp(), "1700000000000");
        assertEquals(1_700_000_000_000L, date.getTime());
    }

    @Test
    void unixTimestampReturnsNullForBlankOrNonNumeric() {
        assertNull(parse(new DumAemUnixTimestamp(), ""));
        assertNull(parse(new DumAemUnixTimestamp(), "abc"));
    }
}
