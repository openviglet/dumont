/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler.fetch;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCFetchException;
import com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCFetchRequest;
import com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCFetchResponse;
import com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCFetcher;

/**
 * The default {@link DumWCFetcher} (roadmap D07): a jsoup HTTP client — the exact posture the crawler
 * used inline before the fetch step was extracted, so the default path is unchanged. Raw bytes are
 * returned for the caller's charset detection (D05); a non-2xx status is surfaced as a {@link
 * DumWCFetchException} so the crawl loop can back off on {@code 429}/{@code 403}.
 *
 * <p>
 * Conditional validators (roadmap D12) are sent as {@code If-None-Match} / {@code If-Modified-Since}
 * when present, and the {@code ETag} / {@code Last-Modified} response headers are carried back. A
 * {@code 304 Not Modified} is returned as a normal response (it is below the {@code 400} error
 * threshold, so jsoup does not raise {@link HttpStatusException}); the crawl loop reads
 * {@link DumWCFetchResponse#notModified()} and skips re-indexing without a body transfer.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class DumWCJsoupFetcher implements DumWCFetcher {

    public static final String NAME = "jsoup";
    private static final String AUTHORIZATION = "Authorization";
    private static final String IF_NONE_MATCH = "If-None-Match";
    private static final String IF_MODIFIED_SINCE = "If-Modified-Since";
    private static final String ETAG = "ETag";
    private static final String LAST_MODIFIED = "Last-Modified";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public DumWCFetchResponse fetch(DumWCFetchRequest request) throws IOException {
        Connection connection = Jsoup.connect(request.url())
                .userAgent(request.userAgent())
                .referrer(request.referrer())
                .timeout(request.timeoutMs())
                .ignoreContentType(true);
        if (StringUtils.isNotBlank(request.authorization())) {
            connection.header(AUTHORIZATION, request.authorization());
        }
        if (StringUtils.isNotBlank(request.ifNoneMatch())) {
            connection.header(IF_NONE_MATCH, request.ifNoneMatch());
        }
        if (StringUtils.isNotBlank(request.ifModifiedSince())) {
            connection.header(IF_MODIFIED_SINCE, request.ifModifiedSince());
        }
        try {
            Connection.Response response = connection.execute();
            return new DumWCFetchResponse(response.statusCode(), response.contentType(),
                    response.bodyAsBytes(), response.url().toString(),
                    response.header(ETAG), response.header(LAST_MODIFIED));
        } catch (HttpStatusException e) {
            throw new DumWCFetchException(e.getStatusCode(), e.getMessage(), e);
        }
    }
}
