/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 */
package com.viglet.dumont.commons.logging;

import java.util.function.LongSupplier;

import lombok.extern.slf4j.Slf4j;

/**
 * Says out loud that indexing-log rows are being lost — once per outage, not once per row (roadmap
 * <strong>D78</strong>).
 *
 * <p>
 * D75 made an unreadable log say so on the read path. This is the write path, where the same
 * failure was invisible for a different reason: the Mongo appender let the exception escape into
 * {@code AppenderBase.doAppend}, which reports the first three occurrences to Logback's status
 * manager — not the application log — and is silent after that; the Redis appender caught it and
 * logged at {@code INFO} with only the message. Either way, an operator reading a short history
 * after an outage had no way to tell rows that were lost from events that never happened.
 *
 * <p>
 * The throttle exists because the alternative is worse than silence. A store that is down fails
 * every row, and one warning per row turns a logging outage into a log flood that buries the
 * warning it is made of. So the first failure is reported immediately, repeats are counted rather
 * than printed, and a line is emitted at most once per quiet period. Recovery is reported too: how
 * many rows were lost is the number an operator actually needs, and it is only knowable at the end.
 *
 * <p>
 * <strong>Where these lines go matters.</strong> The root logger in the {@code mongo} and
 * {@code redis} profiles points at the store's own general appender, so reporting through SLF4J
 * without care would queue the warning behind an {@code AsyncAppender} into the very store that is
 * refusing writes — lost at best, a feedback loop at worst. The logback profiles therefore route
 * {@code com.viglet.dumont.commons.logging} to the file and console appenders with
 * {@code additivity="false"}, which is what makes this class's output reach a reader at all.
 *
 * <p>
 * Thread-safe on purpose: Logback may call an appender from any thread and the counters here are
 * the whole point, so the state changes are synchronized. {@link #wrote()} reads a volatile first
 * and takes the lock only when there is an outage to close.
 */
@Slf4j
class DumLogWriteFailureReporter {

    /** How long to count quietly before saying the outage is still going. */
    static final long DEFAULT_QUIET_PERIOD_MS = 60_000L;

    /**
     * Which log is losing rows — "Indexing log" or "Server log" (D82).
     *
     * <p>
     * Both pairs of appenders report through this class, and they write different logs to the same
     * store. A line that says "Indexing log" while the server log is the one going missing sends
     * the reader to the wrong screen, which is the failure mode this whole track exists to remove.
     */
    private final String logName;

    private final String engine;
    private final long quietPeriodMs;
    private final LongSupplier clock;

    /**
     * Volatile so {@link #wrote()} can answer the ordinary case without taking the lock (D82).
     * Once the general appenders hold one of these, this runs on every log line the connector
     * emits, and the ordinary case is "nothing is wrong".
     */
    private volatile boolean failing;

    private long lostInThisOutage;
    private long lostSinceLastReport;
    private long lastReportAt;

    DumLogWriteFailureReporter(String logName, String engine) {
        this(logName, engine, DEFAULT_QUIET_PERIOD_MS, System::currentTimeMillis);
    }

    /** The clock is a seam so a test can prove the throttle without waiting a minute for it. */
    DumLogWriteFailureReporter(String logName, String engine, long quietPeriodMs,
            LongSupplier clock) {
        this.logName = logName;
        this.engine = engine;
        this.quietPeriodMs = quietPeriodMs;
        this.clock = clock;
    }

    /** One row did not reach the store. */
    synchronized void failed(Exception cause) {
        long now = clock.getAsLong();
        lostInThisOutage++;
        lostSinceLastReport++;
        if (!failing) {
            failing = true;
            log.warn("{}: a row could not be written to {}. Rows are being lost while this lasts, "
                    + "so the history will read short rather than empty.", logName, engine, cause);
            startQuietPeriod(now);
            return;
        }
        if (now - lastReportAt >= quietPeriodMs) {
            log.warn("{}: {} is still refusing writes. {} further row(s) lost since the last "
                    + "report, {} in this outage.", logName, engine, lostSinceLastReport,
                    lostInThisOutage, cause);
            startQuietPeriod(now);
        }
    }

    /**
     * One row reached the store. Silent in the ordinary case — this is called on every successful
     * write — and reports only the transition out of an outage, which is where the total lives.
     */
    void wrote() {
        // The lock-free fast path: on a healthy connector this is every log line, and the answer
        // is a single volatile read.
        if (!failing) {
            return;
        }
        synchronized (this) {
            if (!failing) {
                return;
            }
            log.warn("{}: writes to {} have resumed. {} row(s) were lost while it was unreachable "
                    + "and will not appear in the history.", logName, engine, lostInThisOutage);
            failing = false;
            lostInThisOutage = 0;
            lostSinceLastReport = 0;
        }
    }

    private void startQuietPeriod(long now) {
        lastReportAt = now;
        lostSinceLastReport = 0;
    }
}
