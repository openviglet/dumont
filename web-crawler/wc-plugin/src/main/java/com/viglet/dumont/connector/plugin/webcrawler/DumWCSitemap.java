/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

/**
 * Parsed view of a {@code sitemap.xml} document (roadmap D06).
 *
 * <p>
 * A sitemap is either a {@code <sitemapindex>} pointing at child sitemaps or a {@code <urlset>}
 * listing page URLs; {@link #index} tells the two apart so the caller can recurse into children or
 * seed pages. The {@code <loc>} values are returned verbatim (trimmed); URL scope/normalization is
 * left to the crawler, which already enforces it on every queued link.
 *
 * <p>
 * For a {@code <urlset>}, the optional {@code <lastmod>} of each entry is captured in {@link #lastmods}
 * (keyed by {@code <loc>}) so the crawler can skip a URL whose {@code <lastmod>} is unchanged since the
 * previous crawl, before any fetch (roadmap D12). Index documents carry no lastmods.
 *
 * @param index     whether the document is a {@code <sitemapindex>} (locations are child sitemaps)
 * @param locations the {@code <loc>} values, in document order
 * @param lastmods  {@code <loc>} → {@code <lastmod>} for entries that declare one (empty for indexes)
 */
record DumWCSitemap(boolean index, List<String> locations, Map<String, String> lastmods) {

    static DumWCSitemap parse(byte[] xml, String baseUri) throws IOException {
        Document document = Jsoup.parse(new ByteArrayInputStream(xml), null, baseUri, Parser.xmlParser());
        Elements indexLocs = document.select("sitemapindex > sitemap > loc");
        if (!indexLocs.isEmpty()) {
            List<String> children = indexLocs.stream().map(loc -> loc.text().trim())
                    .filter(StringUtils::isNotBlank).toList();
            return new DumWCSitemap(true, children, Map.of());
        }
        return parseUrlset(document);
    }

    /** The {@code <lastmod>} declared for {@code loc}, or {@code null} when absent. */
    String lastmodOf(String loc) {
        return lastmods.get(loc);
    }

    private static DumWCSitemap parseUrlset(Document document) {
        Elements urls = document.select("urlset > url");
        if (urls.isEmpty()) {
            urls = document.select("url");
        }
        if (urls.isEmpty()) {
            // Malformed sitemap with bare <loc> elements — keep the lenient D06 fallback (no lastmod).
            List<String> locations = document.select("loc").stream().map(loc -> loc.text().trim())
                    .filter(StringUtils::isNotBlank).toList();
            return new DumWCSitemap(false, locations, Map.of());
        }
        List<String> locations = new ArrayList<>();
        Map<String, String> lastmods = new LinkedHashMap<>();
        for (Element url : urls) {
            Element loc = url.selectFirst("loc");
            if (loc == null) {
                continue;
            }
            String location = loc.text().trim();
            if (StringUtils.isBlank(location)) {
                continue;
            }
            locations.add(location);
            Element lastmod = url.selectFirst("lastmod");
            if (lastmod != null && StringUtils.isNotBlank(lastmod.text())) {
                lastmods.put(location, lastmod.text().trim());
            }
        }
        return new DumWCSitemap(false, locations, lastmods);
    }
}
