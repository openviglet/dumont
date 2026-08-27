package com.viglet.dumont.commons.logging;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Date;

import org.bson.Document;

import ch.qos.logback.classic.spi.ILoggingEvent;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Setter
public class DumMongoDBIndexingAppender extends DumMongoDBAppenderBase {

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!enabled || collection == null || eventObject.getArgumentArray() == null) {
            return;
        }
        ObjectMapper mapper = JsonMapper.builder().build();
        Arrays.stream(eventObject.getArgumentArray()).forEach(object -> {
            String json = mapper.writeValueAsString(object);
            collection.insertOne(withBsonDate(Document.parse(json)));
        });
    }

    /**
     * Turns the row's ISO-8601 timestamp into a real BSON date (roadmap <strong>D73</strong>).
     *
     * <p>
     * This is what the {@code ISODate("…")} raw value used to do inside the serializer, moved here
     * — because a serializer that emits Mongo shell syntax produces something no JSON reader
     * accepts, and the Redis engine reads the very same payload back. Stored as a date rather than
     * a string so {@code DumLoggingAPI}'s date-range filter keeps comparing dates to dates.
     */
    static Document withBsonDate(Document document) {
        Object value = document.get(IsoDateSerializer.DATE_FIELD);
        if (value instanceof String iso && !iso.isBlank()) {
            try {
                Instant instant = OffsetDateTime.parse(iso).toInstant();
                document.put(IsoDateSerializer.DATE_FIELD, Date.from(instant));
            } catch (Exception e) {
                // A row whose timestamp cannot be read is still worth keeping; only the date
                // filter is degraded for it, and dropping the row would lose the event entirely.
                log.warn("Indexing log row has an unreadable date '{}', stored as text", iso);
            }
        }
        return document;
    }
}