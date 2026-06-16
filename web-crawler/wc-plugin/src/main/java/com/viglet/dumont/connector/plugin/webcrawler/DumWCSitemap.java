/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
 * @param index     whether the document is a {@code <sitemapindex>} (locations are child sitemaps)
 * @param locations the {@code <loc>} values, in document order
 */
record DumWCSitemap(boolean index, List<String> locations) {

    static DumWCSitemap parse(byte[] xml, String baseUri) throws IOException {
        Document document = Jsoup.parse(new ByteArrayInputStream(xml), null, baseUri, Parser.xmlParser());
        Elements indexLocs = document.select("sitemapindex > sitemap > loc");
        boolean isIndex = !indexLocs.isEmpty();
        Elements locs = isIndex ? indexLocs : pageLocs(document);
        List<String> locations = locs.stream().map(loc -> loc.text().trim())
                .filter(StringUtils::isNotBlank).toList();
        return new DumWCSitemap(isIndex, locations);
    }

    private static Elements pageLocs(Document document) {
        Elements locs = document.select("urlset > url > loc");
        return locs.isEmpty() ? document.select("loc") : locs;
    }
}
