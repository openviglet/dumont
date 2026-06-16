/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.webcrawler.commons.fetch;

import java.nio.charset.StandardCharsets;

/**
 * A transport-agnostic fetch result (roadmap D07). The body is kept as raw bytes so the caller can
 * apply its own charset detection (roadmap D05) instead of trusting the fetcher to have decoded
 * correctly; {@code contentType} carries the declared charset for that detection, and {@code url}
 * is the final URL after redirects.
 *
 * @param statusCode  the HTTP status code (200 on success)
 * @param contentType the {@code Content-Type} header, or {@code null}
 * @param body        the raw response body
 * @param url         the final URL after any redirects
 */
public record DumWCFetchResponse(int statusCode, String contentType, byte[] body, String url) {

    /** The body decoded as UTF-8 — for text resources (robots.txt) where byte fidelity is moot. */
    public String bodyAsString() {
        return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }
}
