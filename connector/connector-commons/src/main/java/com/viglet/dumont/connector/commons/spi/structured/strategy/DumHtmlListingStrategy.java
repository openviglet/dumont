/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.commons.spi.structured.strategy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jsoup.nodes.Document;

import lombok.extern.slf4j.Slf4j;

/**
 * Discovery strategy for sites without a sitemap: fetch each {@link DumListingSource listing page},
 * select the detail anchors under a CSS selector, and resolve their absolute URLs (roadmap
 * <strong>D02</strong>).
 *
 * <p>
 * Anchors are de-duplicated across all listing sources and filtered of non-navigational targets
 * ({@code javascript:}, {@code mailto:}, {@code tel:} and bare {@code #} fragments). Each resolved
 * URL inherits the level declared on the listing source it came from, matching cursarei's
 * HTML-listing crawler.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public final class DumHtmlListingStrategy implements DumDiscoveryStrategy {

    private static final String ABS_HREF = "abs:href";

    private final DumHtmlFetcher fetcher;
    private final List<DumListingSource> sources;

    private DumHtmlListingStrategy(Builder builder) {
        this.fetcher = builder.fetcher;
        this.sources = List.copyOf(builder.sources);
    }

    public static Builder builder(DumHtmlFetcher fetcher) {
        return new Builder(fetcher);
    }

    @Override
    public List<DumDiscoveredUrl> discoverWithLevels() {
        List<DumDiscoveredUrl> discovered = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        for (DumListingSource source : sources) {
            Document document = fetchHtml(source.url());
            if (document == null) {
                continue;
            }
            document.select(source.selector()).forEach(anchor -> {
                String url = anchor.attr(ABS_HREF).trim();
                if (isNavigable(anchor.attr("href").trim()) && !url.isEmpty()
                        && seenUrls.add(url)) {
                    discovered.add(new DumDiscoveredUrl(url, source.level()));
                }
            });
        }
        return discovered;
    }

    private static boolean isNavigable(String href) {
        if (href.isEmpty() || href.startsWith("#")) {
            return false;
        }
        String lower = href.toLowerCase(Locale.ROOT);
        return !lower.startsWith("javascript:") && !lower.startsWith("mailto:")
                && !lower.startsWith("tel:");
    }

    private Document fetchHtml(String url) {
        try {
            return fetcher.html(url);
        } catch (IOException e) {
            log.warn("Could not fetch listing page '{}': {}", url, e.getMessage());
            return null;
        }
    }

    public static final class Builder {
        private final DumHtmlFetcher fetcher;
        private final List<DumListingSource> sources = new ArrayList<>();

        private Builder(DumHtmlFetcher fetcher) {
            if (fetcher == null) {
                throw new IllegalArgumentException("A fetcher is required");
            }
            this.fetcher = fetcher;
        }

        public Builder source(DumListingSource source) {
            this.sources.add(source);
            return this;
        }

        public Builder source(String url, String selector) {
            return source(DumListingSource.of(url, selector));
        }

        public Builder source(String url, String selector, String level) {
            return source(DumListingSource.of(url, selector, level));
        }

        public DumHtmlListingStrategy build() {
            if (sources.isEmpty()) {
                throw new IllegalStateException("At least one listing source is required");
            }
            return new DumHtmlListingStrategy(this);
        }
    }
}
