/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.webcrawler.commons.fetch;

import java.io.IOException;

/**
 * The fetch seam of the web crawler (roadmap D07). The crawler never opens a connection itself — it
 * asks a fetcher for the bytes. This lets the default jsoup client be swapped, per source, for a
 * TLS-fingerprint-impersonating client or a headless renderer without touching the crawl loop,
 * link-extraction, charset detection (D05) or politeness (D06) around it.
 *
 * <p>
 * Implementations are selected by {@link #name()} via the {@code fetcher} config on a source (or the
 * {@code dumont.wc.fetcher} default). A fetcher must throw {@link DumWCFetchException} carrying the
 * status code on an HTTP error so the crawl loop can apply adaptive back-off on {@code 429}/{@code 403}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface DumWCFetcher {

    /** The selector key matched against a source's {@code fetcher} config (case-insensitive). */
    String name();

    /**
     * Fetch a URL.
     *
     * @throws DumWCFetchException on an HTTP error status (so back-off can read the code)
     * @throws IOException         on a transport failure
     */
    DumWCFetchResponse fetch(DumWCFetchRequest request) throws IOException;
}
