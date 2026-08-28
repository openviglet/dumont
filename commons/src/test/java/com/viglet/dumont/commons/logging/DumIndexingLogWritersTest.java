/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 */

package com.viglet.dumont.commons.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoCollection;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

/**
 * D84 — every configuration below answered {@code enabled: true} before this existed, because the
 * endpoint pinged its own client and the client was fine in all of them. What separates them is
 * whether anything is writing, and that is what these assert.
 */
class DumIndexingLogWritersTest {

    private LoggerContext context;
    private Logger logger;

    @BeforeEach
    void setUp() {
        // A private context: attaching store appenders to the real one would rewire the suite's
        // own logging.
        context = new LoggerContext();
        logger = context.getLogger(DumLoggingIndexingLog.class);
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @SuppressWarnings("unchecked")
    private DumMongoDBIndexingAppender startedMongoAppender(boolean enabled) {
        DumMongoDBIndexingAppender appender = new DumMongoDBIndexingAppender();
        appender.setContext(context);
        appender.setEnabled(enabled);
        appender.setCollection(mock(MongoCollection.class));
        appender.start();
        return appender;
    }

    @Test
    @DisplayName("an attached, enabled store appender is writing")
    void attachedAndEnabled() {
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        logger.addAppender(startedMongoAppender(true));

        assertThat(DumIndexingLogWriters.isWriting(logger)).isTrue();
    }

    @Test
    @DisplayName("the default configuration, which declares no store appender at all (D84)")
    void nothingAttached() {
        /*
         * The case that made D84 worth doing. logback-spring.xml is the default `logging.config`,
         * it attaches no store appender to this logger, and an operator who set only
         * dumont.logging.engine lands here — with a reader that pings perfectly.
         */
        logger.setLevel(Level.OFF);
        logger.setAdditive(false);

        assertThat(DumIndexingLogWriters.isWriting(logger)).isFalse();
    }

    @Test
    @DisplayName("a logger switched off is not writing, whatever is attached to it")
    void loggerOffBeatsAnAttachedAppender() {
        // OFF discards the event before any appender sees it, so "an appender is attached" is not
        // the question on its own.
        logger.setLevel(Level.OFF);
        logger.setAdditive(false);
        logger.addAppender(startedMongoAppender(true));

        assertThat(DumIndexingLogWriters.isWriting(logger)).isFalse();
    }

    @Test
    @DisplayName("an appender started with its enabled flag false is not writing")
    void startedButDisabled() {
        // Logback starts it happily; every append then returns immediately. "Started" would have
        // been the wrong thing to check.
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        logger.addAppender(startedMongoAppender(false));

        assertThat(DumIndexingLogWriters.isWriting(logger)).isFalse();
    }

    @Test
    @DisplayName("a store appender wrapped in an AsyncAppender is found (D84)")
    void findsItInsideAnAsyncAppender() {
        // How every shipped profile actually attaches it, so a walk that did not recurse would
        // report false for the one configuration that works.
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);

        AsyncAppender async = new AsyncAppender();
        async.setContext(context);
        async.addAppender(startedMongoAppender(true));
        async.start();
        logger.addAppender(async);

        assertThat(DumIndexingLogWriters.isWriting(logger)).isTrue();

        async.stop();
    }

    @Test
    @DisplayName("an appender inherited from root counts when the logger is additive")
    void findsAnInheritedAppender() {
        logger.setLevel(Level.INFO);
        logger.setAdditive(true);
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(startedMongoAppender(true));

        assertThat(DumIndexingLogWriters.isWriting(logger)).isTrue();
    }

    @Test
    @DisplayName("root's appender is ignored when the logger is not additive")
    void ignoresRootWhenNotAdditive() {
        // additivity="false" is what every shipped profile declares, so inheriting would report a
        // writer this logger's events never reach.
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(startedMongoAppender(true));

        assertThat(DumIndexingLogWriters.isWriting(logger)).isFalse();
    }

    @Test
    @DisplayName("a Redis appender with no pool is not writing")
    void redisWithoutAPool() {
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        DumRedisIndexingAppender appender = new DumRedisIndexingAppender();
        appender.setContext(context);
        appender.setEnabled(true);
        appender.start();
        logger.addAppender(appender);

        assertThat(DumIndexingLogWriters.isWriting(logger)).isFalse();
    }
}
