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
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class DumWCJsoupFetcher implements DumWCFetcher {

    public static final String NAME = "jsoup";
    private static final String AUTHORIZATION = "Authorization";

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
        try {
            Connection.Response response = connection.execute();
            return new DumWCFetchResponse(response.statusCode(), response.contentType(),
                    response.bodyAsBytes(), response.url().toString());
        } catch (HttpStatusException e) {
            throw new DumWCFetchException(e.getStatusCode(), e.getMessage(), e);
        }
    }
}
