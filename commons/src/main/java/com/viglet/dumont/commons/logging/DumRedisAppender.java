/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 */
package com.viglet.dumont.commons.logging;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private static String cachedHostName;

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .addModule(new JodaModule())
            .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, true)
            .build();

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    static {
        try {
            cachedHostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            cachedHostName = "unknown";
        }
    }

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
                .clusterNode(cachedHostName)
                .build();

        executor.submit(() -> {
            try {
                String json = MAPPER.writeValueAsString(logEntry);
                pool.lpush(key, json);
                if (maxEntries > 0) {
                    pool.ltrim(key, 0, maxEntries - 1L);
                }
            } catch (Exception e) {
                log.info("Failed to log to Redis: {}", e.getMessage());
            }
        });
    }

    @Override
    public void stop() {
        executor.shutdown();
        super.stop();
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
