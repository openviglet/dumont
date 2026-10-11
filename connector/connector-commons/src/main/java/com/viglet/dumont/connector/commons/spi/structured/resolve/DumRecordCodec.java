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

package com.viglet.dumont.connector.commons.spi.structured.resolve;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance;

/**
 * A {@link DumStructuredRecord} as text and back (roadmap <strong>D109</strong>), so a persistent
 * {@link DumResolutionIndex} can store the members it merges.
 *
 * <p>
 * Each value is written with its Java type, so a date stays a date and a number keeps its width: a
 * {@code String}, {@code Integer}, {@code Long}, {@code Double}, {@code Float}, {@code BigDecimal},
 * {@code Boolean}, {@code Instant}, {@code java.util.Date}, {@code LocalDate},
 * {@code LocalDateTime}, {@code OffsetDateTime} or {@code ZonedDateTime}, or a list of those. Any
 * other value is written as its {@code toString()}. Per-field provenance and the content type travel
 * with the values. Transients of those types travel too; a transient of any other type is left out,
 * since it is working data the publish step never reads.
 *
 * <p>
 * Reading goes back through the schema-bound builder, so a field the schema no longer declares is
 * dropped rather than published.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public final class DumRecordCodec {

    private static final String TYPE = "t";
    private static final String VALUE = "v";
    private static final String CONFIDENCE = "confidence";

    private DumRecordCodec() {
    }

    /** {@code rec} as JSON text. */
    public static String encode(DumStructuredRecord rec) {
        JSONObject values = new JSONObject();
        rec.values().forEach((field, value) -> values.put(field, encodeValue(value)));
        JSONObject provenance = new JSONObject();
        rec.provenance().forEach((field, origin) -> provenance.put(field, new JSONObject()
                .put("strategy", origin.strategy())
                .putOpt("selector", origin.selector())
                .putOpt(CONFIDENCE, origin.confidence())
                .putOpt("source", origin.source())));
        JSONObject transients = new JSONObject();
        rec.transients().forEach((key, value) -> {
            if (supported(value)) {
                transients.put(key, encodeValue(value));
            }
        });
        return new JSONObject()
                .put("id", rec.id())
                .putOpt("contentType", rec.contentType())
                .put("values", values)
                .put("provenance", provenance)
                .put("transients", transients)
                .toString();
    }

    /** The record {@code json} holds, rebuilt against {@code schema}. */
    public static DumStructuredRecord decode(String json, DumStructuredSchema schema) {
        JSONObject root = new JSONObject(json);
        DumStructuredRecord.Builder builder = DumStructuredRecord.builder(root.getString("id"), schema)
                .contentType(root.optString("contentType", null));
        // Only the org.json API android-json shares: spring-boot-starter-test puts that copy first.
        JSONObject values = root.getJSONObject("values");
        JSONObject provenance = root.getJSONObject("provenance");
        for (Iterator<String> fields = values.keys(); fields.hasNext();) {
            String field = fields.next();
            if (schema.hasField(field)) {
                builder.set(field, decodeValue(values.get(field)), provenance(provenance.optJSONObject(field)));
            }
        }
        JSONObject transients = root.getJSONObject("transients");
        for (Iterator<String> keys = transients.keys(); keys.hasNext();) {
            String key = keys.next();
            builder.transientValue(key, decodeValue(transients.get(key)));
        }
        return builder.build();
    }

    private static DumFieldProvenance provenance(JSONObject origin) {
        if (origin == null) {
            return null;
        }
        Double confidence = origin.has(CONFIDENCE) ? origin.getDouble(CONFIDENCE) : null;
        return new DumFieldProvenance(origin.getString("strategy"), origin.optString("selector", null),
                confidence, origin.optString("source", null));
    }

    private static boolean supported(Object value) {
        if (value instanceof Collection<?> values) {
            return values.stream().allMatch(DumRecordCodec::supported);
        }
        return value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Instant || value instanceof Date || value instanceof LocalDate
                || value instanceof LocalDateTime || value instanceof OffsetDateTime
                || value instanceof ZonedDateTime;
    }

    private static Object encodeValue(Object value) {
        if (value instanceof Collection<?> values) {
            JSONArray list = new JSONArray();
            values.forEach(item -> list.put(encodeValue(item)));
            return new JSONObject().put(TYPE, "list").put(VALUE, list);
        }
        return new JSONObject().put(TYPE, typeOf(value)).put(VALUE, textOf(value));
    }

    private static String typeOf(Object value) {
        return switch (value) {
            case Integer ignored -> "int";
            case Long ignored -> "long";
            case Double ignored -> "double";
            case Float ignored -> "float";
            case BigDecimal ignored -> "decimal";
            case Boolean ignored -> "bool";
            case Instant ignored -> "instant";
            case Date ignored -> "date";
            case LocalDate ignored -> "localDate";
            case LocalDateTime ignored -> "localDateTime";
            case OffsetDateTime ignored -> "offsetDateTime";
            case ZonedDateTime ignored -> "zonedDateTime";
            default -> "string";
        };
    }

    private static String textOf(Object value) {
        return value instanceof Date date ? Long.toString(date.getTime()) : String.valueOf(value);
    }

    private static Object decodeValue(Object encoded) {
        JSONObject tagged = (JSONObject) encoded;
        String type = tagged.getString(TYPE);
        if ("list".equals(type)) {
            JSONArray items = tagged.getJSONArray(VALUE);
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) {
                list.add(decodeValue(items.get(i)));
            }
            return list;
        }
        String text = tagged.getString(VALUE);
        return switch (type) {
            case "int" -> Integer.valueOf(text);
            case "long" -> Long.valueOf(text);
            case "double" -> Double.valueOf(text);
            case "float" -> Float.valueOf(text);
            case "decimal" -> new BigDecimal(text);
            case "bool" -> Boolean.valueOf(text);
            case "instant" -> Instant.parse(text);
            case "date" -> new Date(Long.parseLong(text));
            case "localDate" -> LocalDate.parse(text);
            case "localDateTime" -> LocalDateTime.parse(text);
            case "offsetDateTime" -> OffsetDateTime.parse(text);
            case "zonedDateTime" -> ZonedDateTime.parse(text);
            default -> text;
        };
    }
}
