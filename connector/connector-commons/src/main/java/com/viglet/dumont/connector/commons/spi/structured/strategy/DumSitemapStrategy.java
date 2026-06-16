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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import lombok.extern.slf4j.Slf4j;

/**
 * Discovery strategy that parses one or more {@code sitemap.xml} documents, follows nested sitemap
 * indexes, and classifies each page URL into a level by pattern (roadmap <strong>D02</strong>).
 *
 * <p>
 * Configure it with the sitemap URLs and an ordered list of {@link DumUrlLevelRule}s; the first rule
 * whose pattern matches a URL wins. When level rules are declared, a URL is discovered only if it
 * classifies into a level (and, optionally, into one of the {@link Builder#includeLevels included}
 * levels) — unclassified URLs are dropped, mirroring cursarei's sitemap crawler. With no level rules
 * the strategy simply yields every {@code <loc>} it finds.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public final class DumSitemapStrategy implements DumDiscoveryStrategy {

    private final DumHtmlFetcher fetcher;
    private final List<String> sitemapUrls;
    private final List<DumUrlLevelRule> levelRules;
    private final Set<String> includeLevels;
    private final boolean followSitemapIndex;

    private DumSitemapStrategy(Builder builder) {
        this.fetcher = builder.fetcher;
        this.sitemapUrls = List.copyOf(builder.sitemapUrls);
        this.levelRules = List.copyOf(builder.levelRules);
        this.includeLevels = Set.copyOf(builder.includeLevels);
        this.followSitemapIndex = builder.followSitemapIndex;
    }

    public static Builder builder(DumHtmlFetcher fetcher) {
        return new Builder(fetcher);
    }

    @Override
    public List<DumDiscoveredUrl> discoverWithLevels() {
        List<DumDiscoveredUrl> discovered = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        Set<String> visitedSitemaps = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(sitemapUrls);
        while (!pending.isEmpty()) {
            String sitemapUrl = pending.poll();
            if (!visitedSitemaps.add(sitemapUrl)) {
                continue;
            }
            Document document = fetchXml(sitemapUrl);
            if (document == null) {
                continue;
            }
            Elements nested = document.select("sitemap > loc");
            if (followSitemapIndex && !nested.isEmpty()) {
                nested.forEach(loc -> enqueue(pending, loc.text()));
                continue;
            }
            pageLocs(document).forEach(loc -> collect(loc.text(), seenUrls, discovered));
        }
        return discovered;
    }

    /** The level a URL classifies into, if any rule matches. */
    public Optional<String> classify(String url) {
        return levelRules.stream().filter(rule -> rule.matches(url))
                .map(DumUrlLevelRule::level).findFirst();
    }

    private void collect(String rawUrl, Set<String> seenUrls, List<DumDiscoveredUrl> discovered) {
        String url = clean(rawUrl);
        if (url == null || !seenUrls.add(url)) {
            return;
        }
        if (levelRules.isEmpty()) {
            discovered.add(DumDiscoveredUrl.of(url));
            return;
        }
        classify(url).filter(level -> includeLevels.isEmpty() || includeLevels.contains(level))
                .ifPresent(level -> discovered.add(new DumDiscoveredUrl(url, level)));
    }

    private static Elements pageLocs(Document document) {
        Elements locs = document.select("url > loc");
        return locs.isEmpty() ? document.select("loc") : locs;
    }

    private void enqueue(Deque<String> pending, String rawUrl) {
        String url = clean(rawUrl);
        if (url != null) {
            pending.add(url);
        }
    }

    private Document fetchXml(String sitemapUrl) {
        try {
            return fetcher.xml(sitemapUrl);
        } catch (IOException e) {
            log.warn("Could not fetch sitemap '{}': {}", sitemapUrl, e.getMessage());
            return null;
        }
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class Builder {
        private final DumHtmlFetcher fetcher;
        private final List<String> sitemapUrls = new ArrayList<>();
        private final List<DumUrlLevelRule> levelRules = new ArrayList<>();
        private final Set<String> includeLevels = new LinkedHashSet<>();
        private boolean followSitemapIndex = true;

        private Builder(DumHtmlFetcher fetcher) {
            if (fetcher == null) {
                throw new IllegalArgumentException("A fetcher is required");
            }
            this.fetcher = fetcher;
        }

        public Builder sitemap(String url) {
            this.sitemapUrls.add(url);
            return this;
        }

        public Builder sitemaps(Iterable<String> urls) {
            urls.forEach(this.sitemapUrls::add);
            return this;
        }

        /** Add a classification rule (evaluated in order; first match wins). */
        public Builder level(String regex, String level) {
            this.levelRules.add(DumUrlLevelRule.of(regex, level));
            return this;
        }

        public Builder level(DumUrlLevelRule rule) {
            this.levelRules.add(rule);
            return this;
        }

        /** Restrict discovery to URLs classified into one of these levels (empty = all classified). */
        public Builder includeLevels(String... levels) {
            this.includeLevels.addAll(List.of(levels));
            return this;
        }

        /** Whether {@code <sitemapindex>} documents are followed into their nested sitemaps. */
        public Builder followSitemapIndex(boolean follow) {
            this.followSitemapIndex = follow;
            return this;
        }

        public DumSitemapStrategy build() {
            if (sitemapUrls.isEmpty()) {
                throw new IllegalStateException("At least one sitemap URL is required");
            }
            return new DumSitemapStrategy(this);
        }
    }
}
