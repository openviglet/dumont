/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 */

package com.viglet.dumont.commons.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * D78 — the write path had two ways of saying nothing: an exception left to
 * {@code AppenderBase.doAppend}, which tells the status manager three times and then stops, and a
 * catch that logged at {@code INFO}. This asserts what replaced them, including the throttle,
 * because a warning per lost row would bury itself.
 */
class DumLogWriteFailureReporterTest {

    private static final long QUIET_MS = 60_000L;

    private final AtomicLong now = new AtomicLong(1_000L);
    private ListAppender<ILoggingEvent> captured;
    private Logger reporterLogger;

    private DumLogWriteFailureReporter reporter() {
        return new DumLogWriteFailureReporter("Indexing log", "MongoDB", QUIET_MS, now::get);
    }

    private List<String> warnings() {
        return captured.list.stream().filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage).toList();
    }

    @BeforeEach
    void captureTheReportersOwnLog() {
        reporterLogger = (Logger) LoggerFactory.getLogger(DumLogWriteFailureReporter.class);
        captured = new ListAppender<>();
        captured.start();
        reporterLogger.addAppender(captured);
    }

    @AfterEach
    void releaseIt() {
        reporterLogger.detachAppender(captured);
    }

    @Test
    @DisplayName("the first lost row is reported at WARN, not at INFO and not to nobody")
    void firstFailureIsReported() {
        reporter().failed(new IllegalStateException("connection refused"));

        assertThat(warnings()).singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("MongoDB").contains("read short rather than empty");
    }

    @Test
    @DisplayName("a sustained outage does not become a log flood of its own")
    void repeatsInsideTheQuietPeriodAreCountedNotPrinted() {
        DumLogWriteFailureReporter reporter = reporter();
        for (int i = 0; i < 500; i++) {
            now.addAndGet(10);
            reporter.failed(new IllegalStateException("connection refused"));
        }

        // 500 lost rows inside one quiet period is one line, not five hundred.
        assertThat(warnings()).hasSize(1);
    }

    @Test
    @DisplayName("an outage that outlives the quiet period says so, with the count")
    void aSecondLineFollowsTheQuietPeriod() {
        DumLogWriteFailureReporter reporter = reporter();
        reporter.failed(new IllegalStateException("connection refused"));
        for (int i = 0; i < 9; i++) {
            reporter.failed(new IllegalStateException("connection refused"));
        }
        now.addAndGet(QUIET_MS);
        reporter.failed(new IllegalStateException("connection refused"));

        assertThat(warnings()).hasSize(2);
        // Nine counted quietly plus the tenth that broke the silence, and eleven in the outage.
        assertThat(warnings().get(1)).contains("still refusing writes").contains("10 further")
                .contains("11 in this outage");
    }

    @Test
    @DisplayName("recovery names the number of rows that will never be in the history (D78)")
    void recoveryReportsTheTotalLost() {
        DumLogWriteFailureReporter reporter = reporter();
        for (int i = 0; i < 7; i++) {
            reporter.failed(new IllegalStateException("connection refused"));
        }
        reporter.wrote();

        assertThat(warnings()).hasSize(2);
        assertThat(warnings().get(1)).contains("have resumed").contains("7 row(s) were lost");
    }

    @Test
    @DisplayName("a healthy appender says nothing at all")
    void successesAreSilent() {
        DumLogWriteFailureReporter reporter = reporter();
        for (int i = 0; i < 100; i++) {
            reporter.wrote();
        }

        // `wrote()` runs on every successful row, so anything it emits here is emitted per row.
        assertThat(captured.list).isEmpty();
    }

    @Test
    @DisplayName("the line names which log is losing rows, not just which store (D82)")
    void namesTheLogNotOnlyTheEngine() {
        // Both pairs of appenders report through this class and write different logs to the same
        // store. "Indexing log" when the server log is the one going missing sends the reader to
        // the wrong screen.
        new DumLogWriteFailureReporter("Server log", "Redis", QUIET_MS, now::get)
                .failed(new IllegalStateException("connection refused"));

        assertThat(warnings()).singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .startsWith("Server log:").contains("Redis");
    }

    @Test
    @DisplayName("a second outage starts its count from zero")
    void theCounterResetsBetweenOutages() {
        DumLogWriteFailureReporter reporter = reporter();
        reporter.failed(new IllegalStateException("first"));
        reporter.failed(new IllegalStateException("first"));
        reporter.wrote();
        reporter.failed(new IllegalStateException("second"));
        reporter.wrote();

        // The last line is the second outage's recovery: one row, not three.
        assertThat(warnings()).hasSize(4);
        assertThat(warnings().get(3)).contains("1 row(s) were lost");
    }
}
