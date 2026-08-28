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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoCollection;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * The general appender writes the server log and is attached to <strong>root</strong>, and it had
 * no test — the same gap D79 found beside it, on the appender that matters most when something has
 * gone wrong.
 *
 * <p>
 * D80 is the reason it has one now: the cluster node used to be a name-service call per event and
 * is now read from {@link DumClusterNode}. What that has to keep true is that the field is still
 * stamped, and stamped identically on every row.
 */
class DumMongoDBAppenderTest {

    private static final long WHEN = 1_700_000_000_000L;

    private List<Document> inserted;
    private MongoCollection<Document> collection;
    private DumMongoDBAppender appender;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        inserted = new CopyOnWriteArrayList<>();
        collection = mock(MongoCollection.class);
        // Returning null rather than the boolean from List.add: insertOne returns an
        // InsertOneResult, and handing Mockito a Boolean makes every call throw ClassCastException
        // — which an appender that catches per row would then count as a lost write, quietly
        // testing the failure path while the assertions still passed.
        doAnswer(invocation -> {
            inserted.add(invocation.getArgument(0));
            return null;
        }).when(collection).insertOne(any(Document.class));

        appender = new DumMongoDBAppender();
        appender.setEnabled(true);
        appender.setCollection(collection);
    }

    /** See {@code DumMongoDBIndexingAppenderTest} for why this calls {@code append} directly. */
    private static ILoggingEvent eventFrom(String logger, String message) {
        ILoggingEvent event = mock(ILoggingEvent.class);
        doAnswer(invocation -> Level.WARN).when(event).getLevel();
        doAnswer(invocation -> logger).when(event).getLoggerName();
        doAnswer(invocation -> message).when(event).getFormattedMessage();
        doAnswer(invocation -> WHEN).when(event).getTimeStamp();
        doAnswer(invocation -> null).when(event).getThrowableProxy();
        return event;
    }

    @Test
    @DisplayName("a server-log row carries its level, logger, message and date")
    void writesTheEvent() {
        appender.append(eventFrom("com.viglet.dumont.Something", "it broke"));

        assertThat(inserted).singleElement().satisfies(document -> {
            assertThat(document.getString("level")).isEqualTo("WARN");
            assertThat(document.getString("message")).isEqualTo("it broke");
            assertThat(document.getString("logger")).isEqualTo("com.viglet.dumont.Something");
            assertThat(document.get("date")).isEqualTo(new Date(WHEN));
        });
    }

    @Test
    @DisplayName("the cluster node is stamped, and is the same on every row (D80)")
    void stampsOneClusterNodeOnEveryRow() {
        /*
         * D80 — this used to be InetAddress.getLocalHost() per event. Resolving it once cannot be
         * asserted by timing without making the test a benchmark, so what is asserted is the
         * property that makes resolving once correct: the value does not vary between rows, and it
         * is the shared constant rather than something this class computed for itself.
         */
        for (int i = 0; i < 25; i++) {
            appender.append(eventFrom("com.viglet.dumont.Something", "line " + i));
        }

        assertThat(inserted).hasSize(25)
                .extracting(document -> document.getString("clusterNode"))
                .containsOnly(DumClusterNode.NAME);
        assertThat(DumClusterNode.NAME).isNotBlank();
    }

    @Test
    @DisplayName("a long package name is abbreviated rather than stored whole")
    void abbreviatesALongLoggerName() {
        String tooLong = "com.viglet.dumont.connector.commons.something.very.deep.IndeedAClassName";
        appender.append(eventFrom(tooLong, "hello"));

        assertThat(inserted).singleElement().satisfies(document -> assertThat(
                document.getString("logger")).isNotEqualTo(tooLong).contains("IndeedAClassName"));
    }

    @Test
    @DisplayName("a refused insert is reported at WARN instead of vanishing (D82)")
    void aRefusedInsertIsReported() {
        /*
         * D82 — this insert used to run under no catch, so what escaped went to Logback's status
         * manager three times and then nowhere. Asserting the WARN and not merely "does not
         * throw": a catch that swallows would satisfy the weaker assertion perfectly, which is the
         * exact shape of the bug being fixed.
         */
        Logger reporterLogger =
                (Logger) LoggerFactory.getLogger(DumLogWriteFailureReporter.class);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        reporterLogger.addAppender(captured);
        try {
            doThrow(new IllegalStateException("connection refused"))
                    .when(collection).insertOne(any(Document.class));

            assertThatCode(() -> appender.append(eventFrom("com.viglet.dumont.X", "it broke")))
                    .doesNotThrowAnyException();

            assertThat(captured.list)
                    .filteredOn(logged -> logged.getLevel() == Level.WARN)
                    .singleElement(org.assertj.core.api.InstanceOfAssertFactories.type(
                            ILoggingEvent.class))
                    .extracting(ILoggingEvent::getFormattedMessage,
                            org.assertj.core.api.InstanceOfAssertFactories.STRING)
                    .startsWith("Server log:").contains("MongoDB");
        } finally {
            reporterLogger.detachAppender(captured);
        }
    }

    @Test
    @DisplayName("a disabled appender writes nothing")
    void disabledWritesNothing() {
        appender.setEnabled(false);
        appender.append(eventFrom("com.viglet.dumont.Something", "it broke"));

        assertThat(inserted).isEmpty();
    }
}
