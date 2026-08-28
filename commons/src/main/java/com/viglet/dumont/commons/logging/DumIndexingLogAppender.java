/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 */
package com.viglet.dumont.commons.logging;

/**
 * An appender that persists indexing-log rows, and can say whether it would actually write one
 * (roadmap <strong>D84</strong>).
 *
 * <p>
 * {@code logging/status} used to answer entirely for the reader: it pinged the client
 * {@code DumLoggingAPI} holds and reported that. The rows are written by one of these, configured
 * separately through {@code logging.config}, so the endpoint could report a healthy log over a
 * history that was empty and always would be — the failure that reads as an absence, one level
 * above the code that spent D73 to D82 removing it.
 *
 * <p>
 * Implemented rather than inferred, because "started" is not the question. Logback starts an
 * appender whose {@code enabled} flag is false and whose store handle is null; it simply returns
 * from every {@code append}. The question worth answering is whether a row given to this appender
 * right now would reach a store.
 */
public interface DumIndexingLogAppender {

    /** Whether a row handed to this appender now would be written, rather than dropped. */
    boolean isWritingEnabled();
}
