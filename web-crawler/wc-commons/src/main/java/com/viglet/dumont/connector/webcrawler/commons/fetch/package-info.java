/*
 * Copyright (C) 2016-2026 the original author or authors.
 */

/**
 * The pluggable fetch seam of the web crawler (roadmap <strong>D07</strong>): {@link
 * com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCFetcher} and its request/response
 * value types. Lives in {@code wc-commons} so alternative fetchers (TLS-impersonating, headless)
 * can implement it without depending on the plugin internals.
 */
package com.viglet.dumont.connector.webcrawler.commons.fetch;
