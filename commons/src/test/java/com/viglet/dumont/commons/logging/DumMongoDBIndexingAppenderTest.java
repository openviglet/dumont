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

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoCollection;
import com.viglet.dumont.commons.indexing.DumIndexingStatus;
import com.viglet.dumont.commons.indexing.DumLoggingStatus;

import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * {@code append} had no test at all — only {@code withBsonDate} did — which is how a mapper built
 * per event survived unnoticed on the busiest path in the connector (roadmap <strong>D79</strong>),
 * and why D78's rewrite of the same method landed unguarded.
 *
 * <p>
 * The mapper is now {@code static final}. What that trades is construction cost for sharing, so
 * the assertions that matter are that a row still serialises correctly and that concurrent
 * appends — which is how Logback actually calls an appender — still produce correct rows.
 */
class DumMongoDBIndexingAppenderTest {

    private static final Instant WHEN = Instant.ofEpochMilli(1_700_000_000_000L);

    private List<Document> inserted;
    private MongoCollection<Document> collection;
    private DumMongoDBIndexingAppender appender;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        inserted = new CopyOnWriteArrayList<>();
        collection = mock(MongoCollection.class);
        doAnswer(invocation -> inserted.add(invocation.getArgument(0)))
                .when(collection).insertOne(any(Document.class));

        appender = new DumMongoDBIndexingAppender();
        appender.setEnabled(true);
        appender.setCollection(collection);
    }

    private static DumLoggingIndexing row(String contentId) {
        return DumLoggingIndexing.builder().contentId(contentId)
                .status(DumIndexingStatus.INDEXED).resultStatus(DumLoggingStatus.SUCCESS)
                .url("http://site/" + contentId).source("wknd").transactionId("tx-1")
                .date(WHEN).build();
    }

    /*
     * `append` rather than `doAppend`, deliberately. `doAppend` refuses an appender that was never
     * started, and starting this one would build a real client and overwrite the mocked
     * collection. Calling the method under test directly also makes "does not throw" mean our own
     * catch held, rather than AppenderBase's guard having swallowed it — which is the very
     * behaviour D78 existed to stop relying on.
     */
    private static ILoggingEvent eventFor(Object... arguments) {
        ILoggingEvent event = mock(ILoggingEvent.class);
        doAnswer(invocation -> arguments).when(event).getArgumentArray();
        return event;
    }

    @Test
    @DisplayName("a row reaches the collection with its fields and a real BSON date")
    void serialisesARowThroughTheSharedMapper() {
        appender.append(eventFor(row("c1")));

        assertThat(inserted).singleElement().satisfies(document -> {
            assertThat(document.getString("contentId")).isEqualTo("c1");
            assertThat(document.getString("source")).isEqualTo("wknd");
            assertThat(document.getString("transactionId")).isEqualTo("tx-1");
            // Not the ISO string: the date filter compares dates to dates (D73).
            assertThat(document.get("date")).isEqualTo(Date.from(WHEN));
        });
    }

    @Test
    @DisplayName("every argument on one event becomes its own row")
    void writesEachArgument() {
        appender.append(eventFor(row("c1"), row("c2"), row("c3")));

        assertThat(inserted).extracting(document -> document.getString("contentId"))
                .containsExactly("c1", "c2", "c3");
    }

    @Test
    @DisplayName("a row the store refuses costs its own row and nothing behind it (D78)")
    void oneRefusedInsertDoesNotDropTheRest() {
        doThrow(new IllegalStateException("connection refused"))
                .doAnswer(invocation -> inserted.add(invocation.getArgument(0)))
                .when(collection).insertOne(any(Document.class));

        assertThatCode(() -> appender.append(eventFor(row("c1"), row("c2"))))
                .doesNotThrowAnyException();

        assertThat(inserted).extracting(document -> document.getString("contentId"))
                .containsExactly("c2");
    }

    @Test
    @DisplayName("the shared mapper is safe under the concurrency Logback actually uses (D79)")
    void concurrentAppendsAllProduceCorrectRows() throws Exception {
        /*
         * The one thing hoisting the mapper to `static final` could have broken. Jackson mappers
         * are built to be shared, but "built to be" is not the same as "is here", and an appender
         * is called from whichever thread logged.
         */
        int threads = 8;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> running = new java.util.ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int id = t;
                running.add(pool.submit(() -> {
                    for (int i = 0; i < perThread; i++) {
                        appender.append(eventFor(row("c-" + id + "-" + i)));
                    }
                }));
            }
            for (Future<?> future : running) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(inserted).hasSize(threads * perThread);
        // Every row intact and distinct: interleaved serialisation would show up as a missing
        // field, a malformed document or a duplicate id long before it showed up as a count.
        assertThat(inserted).allSatisfy(document -> {
            assertThat(document.getString("source")).isEqualTo("wknd");
            assertThat(document.get("date")).isEqualTo(Date.from(WHEN));
        });
        assertThat(inserted).extracting(document -> document.getString("contentId"))
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("a disabled appender writes nothing")
    void disabledWritesNothing() {
        appender.setEnabled(false);
        appender.append(eventFor(row("c1")));

        assertThat(inserted).isEmpty();
    }
}
