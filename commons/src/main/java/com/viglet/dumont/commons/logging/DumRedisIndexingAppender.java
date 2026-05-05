/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 */
package com.viglet.dumont.commons.logging;

import java.util.Arrays;

import ch.qos.logback.classic.spi.ILoggingEvent;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis counterpart of {@link DumMongoDBIndexingAppender}. Serializes each
 * argument of the logging event as JSON and pushes it to the configured Redis
 * LIST. Trims to {@code maxEntries} so the key doesn't grow unbounded.
 */
@Slf4j
@Setter
public class DumRedisIndexingAppender extends DumRedisAppenderBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!enabled || pool == null) {
            return;
        }
        try {
            Arrays.stream(eventObject.getArgumentArray())
                    .forEach(object -> {
                        String json = MAPPER.writeValueAsString(object);
                        pool.lpush(key, json);
                        if (maxEntries > 0) {
                            pool.ltrim(key, 0, maxEntries - 1L);
                        }
                    });
        } catch (Exception e) {
            log.info("Failed to log indexing event to Redis: {}", e.getMessage());
        }
    }
}
