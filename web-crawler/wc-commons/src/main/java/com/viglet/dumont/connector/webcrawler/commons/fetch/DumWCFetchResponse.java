/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.webcrawler.commons.fetch;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * A transport-agnostic fetch result (roadmap D07). The body is kept as raw bytes so the caller can
 * apply its own charset detection (roadmap D05) instead of trusting the fetcher to have decoded
 * correctly; {@code contentType} carries the declared charset for that detection, and {@code url}
 * is the final URL after redirects.
 *
 * <p>
 * {@code etag} / {@code lastModified} carry the response validators (roadmap D12) so the caller can
 * persist them and issue a conditional request next time; a {@code 304} status ({@link #notModified()})
 * means the resource was unchanged and the body is empty.
 *
 * @param statusCode   the HTTP status code (200 on success, 304 when unchanged)
 * @param contentType  the {@code Content-Type} header, or {@code null}
 * @param body         the raw response body (empty on a {@code 304})
 * @param url          the final URL after any redirects
 * @param etag         the {@code ETag} response header, or {@code null}
 * @param lastModified the {@code Last-Modified} response header, or {@code null}
 */
public record DumWCFetchResponse(int statusCode, String contentType, byte[] body, String url,
        String etag, String lastModified) {

    /** A response with no conditional validators (roadmap D07 default; back-compat for callers/tests). */
    public DumWCFetchResponse(int statusCode, String contentType, byte[] body, String url) {
        this(statusCode, contentType, body, url, null, null);
    }

    /** The body decoded as UTF-8 — for text resources (robots.txt) where byte fidelity is moot. */
    public String bodyAsString() {
        return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }

    /** True when the server answered {@code 304 Not Modified} (roadmap D12): the resource is unchanged. */
    public boolean notModified() {
        return statusCode == 304;
    }

    /** Content-aware equality: the {@code body} array is compared by value, not identity. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DumWCFetchResponse other = (DumWCFetchResponse) o;
        return statusCode == other.statusCode
                && Objects.equals(contentType, other.contentType)
                && Arrays.equals(body, other.body)
                && Objects.equals(url, other.url)
                && Objects.equals(etag, other.etag)
                && Objects.equals(lastModified, other.lastModified);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(statusCode, contentType, url, etag, lastModified)
                + Arrays.hashCode(body);
    }

    /** Body is rendered as its length (raw bytes would flood logs and may be binary). */
    @Override
    public String toString() {
        return "DumWCFetchResponse[statusCode=" + statusCode + ", contentType=" + contentType
                + ", body=" + (body == null ? "null" : body.length + " bytes") + ", url=" + url
                + ", etag=" + etag + ", lastModified=" + lastModified + "]";
    }
}
