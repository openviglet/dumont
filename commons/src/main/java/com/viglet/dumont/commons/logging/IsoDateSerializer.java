package com.viglet.dumont.commons.logging;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Writes an {@link Instant} as an ISO-8601 string with offset.
 *
 * <p>
 * It used to emit the MongoDB shell's {@code ISODate("…")} as a <em>raw</em> value, so that
 * {@code Document.parse} would store a real BSON date. That worked for Mongo and made the same
 * payload invalid JSON everywhere else (roadmap <strong>D73</strong>): the Redis appender writes
 * the identical string to a list, and the log API reads it back with Jackson, which rejected the
 * first row and returned an empty page for every query.
 *
 * <p>
 * So the serializer emits JSON, and the storage detail lives where the storage is:
 * {@link DumMongoDBIndexingAppender} and {@link DumMongoDBAppender} convert the parsed string back
 * to a date before inserting, which is what keeps Mongo's date-range filter working.
 */
public class IsoDateSerializer extends StdSerializer<Instant> {
    // No v3, o construtor padrão chamando super() é fortemente recomendado
    public IsoDateSerializer() {
        super(Instant.class);
    }

    /** The field every appender and reader agrees carries the row's timestamp. */
    public static final String DATE_FIELD = "date";

    @Override
    public void serialize(Instant instant, JsonGenerator jsonGenerator, SerializationContext provider)
            throws JacksonException {
        if (instant == null) {
            jsonGenerator.writeNull();
            return;
        }

        ZonedDateTime zdt = instant.atZone(ZoneId.systemDefault());
        jsonGenerator.writeString(zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }
}
