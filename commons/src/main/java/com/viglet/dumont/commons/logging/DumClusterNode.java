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

/**
 * The name this process stamps on every log row, resolved once (roadmap <strong>D80</strong>).
 *
 * <p>
 * {@code InetAddress.getLocalHost()} is not arithmetic: it goes through the platform name service
 * and can mean a reverse DNS lookup, which on a slow or misconfigured resolver takes real time.
 * {@link DumMongoDBAppender} used to call it per logging event. The cost did not land on
 * application threads — an {@code AsyncAppender} sits in front — so it showed up as the worse kind
 * of problem: the worker drained slower than events arrived, the bounded queue filled, and
 * {@code neverBlock=true} discarded log lines. A slow resolver became missing logs, with nothing
 * connecting the two.
 *
 * <p>
 * The value cannot change between two log lines of the same process, so it is resolved in a static
 * initialiser and read from there. {@link DumRedisAppender} already did this privately; holding it
 * in one place is what stops the two appenders from carrying the same six lines twice.
 *
 * <p>
 * Never throws. A host that cannot name itself is not a reason to fail logging, and
 * {@code "unknown"} is a more honest row than no row.
 */
final class DumClusterNode {

    /** What a host that cannot resolve its own name is called. */
    static final String UNKNOWN = "unknown";

    static final String NAME = resolve();

    private DumClusterNode() {
        // Constants only.
    }

    private static String resolve() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException | RuntimeException e) {
            // No logging here: this runs at class-init, from inside the logging stack itself.
            return UNKNOWN;
        }
    }
}
