/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.webcrawler.commons.fetch;

/**
 * The parameters of a single fetch (roadmap D07). A {@link DumWCFetcher} is handed one of these and
 * is free to honor or ignore any field — a TLS-impersonating client may rewrite the user agent, a
 * headless renderer may ignore the timeout — but the default jsoup fetcher applies them verbatim.
 *
 * @param url           the absolute URL to fetch
 * @param userAgent     the {@code User-Agent} to present (already resolved by the caller's strategy)
 * @param referrer      the {@code Referer} to present, or {@code null}
 * @param timeoutMs     the connect/read timeout in milliseconds
 * @param authorization a ready-to-send {@code Authorization} header value, or {@code null} when none
 */
public record DumWCFetchRequest(String url, String userAgent, String referrer, int timeoutMs,
        String authorization) {

    /** A request with no authorization header. */
    public static DumWCFetchRequest of(String url, String userAgent, String referrer, int timeoutMs) {
        return new DumWCFetchRequest(url, userAgent, referrer, timeoutMs, null);
    }
}
