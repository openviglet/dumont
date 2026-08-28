/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 */
package com.viglet.dumont.commons.logging;

import java.time.Instant;

import org.jetbrains.annotations.NotNull;

import ch.qos.logback.classic.pattern.Abbreviator;
import ch.qos.logback.classic.pattern.TargetLengthBasedClassNameAbbreviator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.CoreConstants;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.joda.JodaModule;

/**
 * Redis counterpart of {@link DumMongoDBAppender}. Captures each logging event
 * as a {@link DumLoggingGeneral} record and pushes the JSON payload to a Redis
 * LIST via {@code LPUSH}. Optionally trims the list to {@code maxEntries}.
 */
@Slf4j
@Setter
public class DumRedisAppender extends DumRedisAppenderBase {

    public static final int MAX_LENGTH_PACKAGE_NAME = 40;
    private static final Abbreviator ABBREVIATOR = new TargetLengthBasedClassNameAbbreviator(1);

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .addModule(new JodaModule())
            .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, true)
            .build();

    @Override
    protected void append(ILoggingEvent event) {
        if (!enabled || pool == null) {
            return;
        }

        DumLoggingGeneral logEntry = DumLoggingGeneral.builder()
                .level(event.getLevel().toString())
                .logger(abbreviatePackage(event.getLoggerName()))
                .message(event.getFormattedMessage())
                .date(Instant.ofEpochMilli(event.getTimeStamp()))
                .stackTrace(getStackTrace(event))
                // D80: the cache this class already had, moved somewhere the Mongo sibling can
                // read it too rather than carrying its own copy of the same six lines.
                .clusterNode(DumClusterNode.NAME)
                .build();

        /*
         * D81 — written here, on the caller's thread, rather than handed to an executor this class
         * used to own.
         *
         * The logback profile already wraps this appender in an AsyncAppender configured
         * queueSize=512, discardingThreshold=0, neverBlock=true: buffer this much, then drop
         * rather than stall the application. A second queue behind it made that policy
         * unreachable — `append` returned as soon as it had submitted, so the async queue always
         * looked empty and never discarded, while the real backlog grew in a
         * LinkedBlockingQueue with no ceiling. Shutdown had the mirror of it: `shutdown()` does
         * not wait, so queued tasks ran after the client had closed, losing exactly the rows from
         * the end of a run. Removing it restores the bound and the discard policy in one move,
         * and matches DumRedisIndexingAppender, which was always synchronous under its own async.
         */
        try {
            String json = MAPPER.writeValueAsString(logEntry);
            pool.lpush(key, json);
            if (maxEntries > 0) {
                pool.ltrim(key, 0, maxEntries - 1L);
            }
        } catch (Exception e) {
            log.info("Failed to log to Redis: {}", e.getMessage());
        }
    }

    private static @NotNull String getStackTrace(ILoggingEvent event) {
        StringBuilder stackTraceBuilder = new StringBuilder();
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy != null) {
            String throwableStr = ThrowableProxyUtil.asString(throwableProxy);
            stackTraceBuilder.append(throwableStr);
            stackTraceBuilder.append(CoreConstants.LINE_SEPARATOR);
        }
        return stackTraceBuilder.toString();
    }

    private static @NotNull String abbreviatePackage(String packageName) {
        if (packageName == null)
            return "";
        if (packageName.length() <= MAX_LENGTH_PACKAGE_NAME)
            return packageName;
        return ABBREVIATOR.abbreviate(packageName);
    }
}
