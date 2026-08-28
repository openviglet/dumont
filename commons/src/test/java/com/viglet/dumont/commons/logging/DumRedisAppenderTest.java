/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 */

package com.viglet.dumont.commons.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import redis.clients.jedis.RedisClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * D81 — this appender used to submit every event to a fixed pool it owned, behind an
 * {@code AsyncAppender} that was already there. The assertions here are what removing that has to
 * keep true, and the first of them is the one that could not have passed before.
 */
class DumRedisAppenderTest {

    private static final String KEY = "dumontLog:server";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private RedisClient pool;
    private DumRedisAppender appender;

    @BeforeEach
    void setUp() {
        pool = mock(RedisClient.class);
        appender = new DumRedisAppender();
        appender.setEnabled(true);
        appender.setPool(pool);
        appender.setKey(KEY);
        appender.setMaxEntries(100);
    }

    /** See {@code DumMongoDBIndexingAppenderTest} for why this calls {@code append} directly. */
    private static ILoggingEvent event(String message) {
        ILoggingEvent event = mock(ILoggingEvent.class);
        org.mockito.Mockito.doReturn(Level.WARN).when(event).getLevel();
        org.mockito.Mockito.doReturn("com.viglet.dumont.Something").when(event).getLoggerName();
        org.mockito.Mockito.doReturn(message).when(event).getFormattedMessage();
        org.mockito.Mockito.doReturn(1_700_000_000_000L).when(event).getTimeStamp();
        org.mockito.Mockito.doReturn(null).when(event).getThrowableProxy();
        return event;
    }

    @Test
    @DisplayName("the row is pushed before append returns, with no queue of its own (D81)")
    void writesSynchronously() {
        /*
         * The whole of D81 in one assertion. With the executor in place this verify raced the
         * pool's threads and the row had usually not been pushed yet — which is precisely the
         * problem: `append` returning early is what made the AsyncAppender in front believe its
         * queue was draining, so its bound and its discard policy never applied to the backlog
         * that was actually building.
         */
        appender.append(event("it broke"));

        verify(pool).lpush(org.mockito.ArgumentMatchers.eq(KEY), anyString());
    }

    @Test
    @DisplayName("the pushed payload is the log row, readable as JSON")
    void pushesTheEventAsJson() {
        appender.append(event("it broke"));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(pool).lpush(org.mockito.ArgumentMatchers.eq(KEY), payload.capture());

        JsonNode row = MAPPER.readTree(payload.getValue());
        assertThat(row.get("message").asString()).isEqualTo("it broke");
        assertThat(row.get("level").asString()).isEqualTo("WARN");
        assertThat(row.get("clusterNode").asString()).isEqualTo(DumClusterNode.NAME);
    }

    @Test
    @DisplayName("the list is trimmed to maxEntries, and left alone when it is unbounded")
    void trimsOnlyWhenBounded() {
        appender.append(event("first"));
        verify(pool).ltrim(KEY, 0, 99L);

        appender.setMaxEntries(0);
        appender.append(event("second"));
        // Still the one call from the bounded append above: nothing new.
        verify(pool).ltrim(KEY, 0, 99L);
        verify(pool, never()).ltrim(KEY, 0, -1L);
    }

    @Test
    @DisplayName("a store that refuses the push does not throw into the logging call")
    void aRefusedPushDoesNotEscape() {
        doThrow(new IllegalStateException("connection refused"))
                .when(pool).lpush(anyString(), anyString());

        assertThatCode(() -> appender.append(event("it broke"))).doesNotThrowAnyException();
        verify(pool, never()).ltrim(anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("a disabled appender writes nothing")
    void disabledWritesNothing() {
        appender.setEnabled(false);
        appender.append(event("it broke"));

        verify(pool, never()).lpush(anyString(), anyString());
    }
}
