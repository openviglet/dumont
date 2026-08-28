package com.viglet.dumont.commons.logging;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;

import org.bson.Document;

import ch.qos.logback.classic.spi.ILoggingEvent;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Setter
public class DumMongoDBIndexingAppender extends DumMongoDBAppenderBase
        implements DumIndexingLogAppender {

    /** D84 — the two conditions {@code append} checks, asked before an event rather than during. */
    @Override
    public boolean isWritingEnabled() {
        return enabled && collection != null;
    }

    /**
     * One mapper for the life of the appender, not one per event (roadmap <strong>D79</strong>).
     *
     * <p>
     * Building an {@code ObjectMapper} sets up the serializer caches, the type factory and the
     * module registry — work meant to be paid once and amortised, which is why the class is built
     * to be shared and is safe to use from any thread once constructed. This method used to
     * construct one per logging event, on the hottest path in the connector: every document emits
     * a row per status it passes through, so the cost scaled with exactly the work the log exists
     * to record. {@link DumRedisIndexingAppender} already held a static one.
     */
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** Says once per outage that rows are being lost, instead of not saying it at all (D78). */
    private final DumLogWriteFailureReporter failures =
            new DumLogWriteFailureReporter("Indexing log", "MongoDB");

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!enabled || collection == null || eventObject.getArgumentArray() == null) {
            return;
        }
        // One row at a time, the way D74 reads them back: a document this insert cannot take is
        // its own loss, not a reason to drop the arguments queued behind it. The catch is also
        // what keeps the exception out of AppenderBase.doAppend, which would have reported three
        // of them to the status manager and then gone quiet.
        for (Object object : eventObject.getArgumentArray()) {
            try {
                String json = MAPPER.writeValueAsString(object);
                collection.insertOne(withBsonDate(Document.parse(json)));
                failures.wrote();
            } catch (Exception e) {
                failures.failed(e);
            }
        }
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