/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.webcrawler.commons.fetch;

/**
 * The parameters of a single fetch (roadmap D07). A {@link DumWCFetcher} is handed one of these and
 * is free to honor or ignore any field — a TLS-impersonating client may rewrite the user agent, a
 * headless renderer may ignore the timeout — but the default jsoup fetcher applies them verbatim.
 *
 * <p>
 * The {@code ifNoneMatch} / {@code ifModifiedSince} validators (roadmap D12) make the request
 * conditional: a fetcher sends them as {@code If-None-Match} / {@code If-Modified-Since} so an
 * unchanged resource comes back as {@code 304 Not Modified} with no body transfer. Both are
 * {@code null} on an unconditional (first-time) fetch.
 *
 * @param url             the absolute URL to fetch
 * @param userAgent       the {@code User-Agent} to present (already resolved by the caller's strategy)
 * @param referrer        the {@code Referer} to present, or {@code null}
 * @param timeoutMs       the connect/read timeout in milliseconds
 * @param authorization   a ready-to-send {@code Authorization} header value, or {@code null} when none
 * @param ifNoneMatch     the {@code ETag} to revalidate against ({@code If-None-Match}), or {@code null}
 * @param ifModifiedSince the {@code Last-Modified} to revalidate against ({@code If-Modified-Since}), or {@code null}
 */
public record DumWCFetchRequest(String url, String userAgent, String referrer, int timeoutMs,
        String authorization, String ifNoneMatch, String ifModifiedSince) {

    /** A request with no authorization header and no conditional validators. */
    public static DumWCFetchRequest of(String url, String userAgent, String referrer, int timeoutMs) {
        return new DumWCFetchRequest(url, userAgent, referrer, timeoutMs, null, null, null);
    }

    /** True when at least one conditional validator is present (roadmap D12). */
    public boolean conditional() {
        return (ifNoneMatch != null && !ifNoneMatch.isBlank())
                || (ifModifiedSince != null && !ifModifiedSince.isBlank());
    }
}
