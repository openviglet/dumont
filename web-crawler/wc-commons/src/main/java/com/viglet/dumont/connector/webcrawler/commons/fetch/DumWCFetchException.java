/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.webcrawler.commons.fetch;

import java.io.IOException;

/**
 * A fetch that failed with an HTTP error status (roadmap D07). Carrying the status code on the
 * exception lets the crawl loop drive its adaptive back-off uniformly across fetcher implementations
 * — every fetcher (jsoup, TLS-impersonating, headless) signals a {@code 429}/{@code 403} the same
 * way, regardless of the client library it wraps.
 */
public class DumWCFetchException extends IOException {

    private final int statusCode;

    public DumWCFetchException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /** The HTTP status code that caused the failure. */
    public int statusCode() {
        return statusCode;
    }
}
