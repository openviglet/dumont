/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 */
package com.viglet.dumont.commons.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import lombok.Setter;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis counterpart of {@link DumMongoDBIndexingAppender}. Serializes each
 * argument of the logging event as JSON and pushes it to the configured Redis
 * LIST. Trims to {@code maxEntries} so the key doesn't grow unbounded.
 */
@Setter
public class DumRedisIndexingAppender extends DumRedisAppenderBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Says once per outage that rows are being lost, at a level someone reads (D78). */
    private final DumLogWriteFailureReporter failures =
            new DumLogWriteFailureReporter("Indexing log", "Redis");

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!enabled || pool == null || eventObject.getArgumentArray() == null) {
            return;
        }
        // Per row rather than per event (D74's lesson on the write side), and reported rather
        // than noted at INFO with the message alone — a row that failed to persist is a hole in
        // the record, and the reader who later sees a short history cannot infer it.
        for (Object object : eventObject.getArgumentArray()) {
            try {
                String json = MAPPER.writeValueAsString(object);
                pool.lpush(key, json);
                if (maxEntries > 0) {
                    pool.ltrim(key, 0, maxEntries - 1L);
                }
                failures.wrote();
            } catch (Exception e) {
                failures.failed(e);
            }
        }
    }
}
