/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 */
package com.viglet.dumont.commons.logging;

import java.util.Iterator;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.spi.AppenderAttachable;

/**
 * Whether anything is actually writing the indexing log (roadmap <strong>D84</strong>).
 *
 * <p>
 * Turning the log on takes two settings that nothing checks against each other.
 * {@code dumont.logging.engine} configures the reader — the client {@code DumLoggingAPI} holds and
 * pings. The writer is a Logback appender chosen by {@code logging.config}, which defaults to
 * {@code logback-spring.xml}: a configuration that declares no store appender at all and sets this
 * logger to {@code OFF}. Set the first and not the second and {@code logging/status} answers
 * {@code enabled: true} over a history that is empty and always will be.
 *
 * <p>
 * This asks the writer directly instead. Two things have to hold: the logger the rows go through is
 * enabled at the level {@link DumLoggingIndexingLog} writes at, and a started appender attached to
 * it says it would write. Both are needed — {@code OFF} discards the event before any appender sees
 * it, and an appender can be started with its {@code enabled} flag false.
 */
public final class DumIndexingLogWriters {

    private DumIndexingLogWriters() {
        // Static entry point only.
    }

    /** Whether an indexing-log row emitted right now would reach a store. */
    public static boolean isWriting() {
        if (LoggerFactory.getLogger(DumLoggingIndexingLog.class) instanceof Logger logger) {
            return isWriting(logger);
        }
        // Not a Logback binding: nothing here can answer, and claiming "yes" would be the lie
        // this whole check exists to remove.
        return false;
    }

    static boolean isWriting(Logger logger) {
        // DumLoggingIndexingLog writes at INFO, so OFF (the default configuration's setting for
        // this logger) means the event never reaches an appender at all.
        if (!logger.isInfoEnabled()) {
            return false;
        }
        if (writesToAStore(logger.iteratorForAppenders())) {
            return true;
        }
        /*
         * Inherited appenders, for the configuration that declares none of its own. Only the root
         * is consulted rather than the whole ancestor chain: Logback exposes no parent accessor,
         * and every configuration this project ships either declares the appender on this logger
         * with additivity="false" or has nothing at all.
         */
        return logger.isAdditive()
                && writesToAStore(logger.getLoggerContext().getLogger(Logger.ROOT_LOGGER_NAME)
                        .iteratorForAppenders());
    }

    /** Depth-first, because the profiles wrap the store appender in an {@code AsyncAppender}. */
    private static boolean writesToAStore(Iterator<Appender<ILoggingEvent>> appenders) {
        while (appenders.hasNext()) {
            Appender<ILoggingEvent> appender = appenders.next();
            if (!appender.isStarted()) {
                continue;
            }
            if (appender instanceof DumIndexingLogAppender store && store.isWritingEnabled()) {
                return true;
            }
            if (appender instanceof AppenderAttachable<?> attachable
                    && writesToAStore(asAttachable(attachable).iteratorForAppenders())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static AppenderAttachable<ILoggingEvent> asAttachable(AppenderAttachable<?> attachable) {
        // An appender attached to an ILoggingEvent logger attaches ILoggingEvent appenders; the
        // wildcard is only what the instanceof test can express.
        return (AppenderAttachable<ILoggingEvent>) attachable;
    }
}
