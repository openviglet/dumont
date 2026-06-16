/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Coverage for roadmap D06 — {@code sitemap.xml} parsing. Distinguishes a {@code <urlset>} (page
 * entry points) from a {@code <sitemapindex>} (child sitemaps to follow) so the crawler can recurse
 * or seed accordingly.
 */
@DisplayName("DumWCSitemap parsing (D06)")
class DumWCSitemapTest {

    private static final String BASE = "https://example.com";

    private static byte[] xml(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("A urlset yields page locations and is not an index")
    void urlset() throws IOException {
        byte[] body = xml("""
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                  <url><loc>https://example.com/a</loc><lastmod>2026-01-01</lastmod></url>
                  <url><loc>https://example.com/b</loc></url>
                </urlset>
                """);

        DumWCSitemap sitemap = DumWCSitemap.parse(body, BASE);

        assertFalse(sitemap.index());
        assertEquals(2, sitemap.locations().size());
        assertTrue(sitemap.locations().contains("https://example.com/a"));
        assertTrue(sitemap.locations().contains("https://example.com/b"));
    }

    @Test
    @DisplayName("A sitemapindex is flagged and yields child sitemap locations")
    void sitemapIndex() throws IOException {
        byte[] body = xml("""
                <?xml version="1.0" encoding="UTF-8"?>
                <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                  <sitemap><loc>https://example.com/sitemap-1.xml</loc></sitemap>
                  <sitemap><loc>https://example.com/sitemap-2.xml</loc></sitemap>
                </sitemapindex>
                """);

        DumWCSitemap sitemap = DumWCSitemap.parse(body, BASE);

        assertTrue(sitemap.index());
        assertEquals(2, sitemap.locations().size());
        assertTrue(sitemap.locations().contains("https://example.com/sitemap-1.xml"));
    }

    @Test
    @DisplayName("A urlset captures <lastmod> per <loc> for incremental skip (D12)")
    void capturesLastmod() throws IOException {
        byte[] body = xml("""
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                  <url><loc>https://example.com/a</loc><lastmod>2026-01-01</lastmod></url>
                  <url><loc>https://example.com/b</loc></url>
                </urlset>
                """);

        DumWCSitemap sitemap = DumWCSitemap.parse(body, BASE);

        assertEquals("2026-01-01", sitemap.lastmodOf("https://example.com/a"));
        assertNull(sitemap.lastmodOf("https://example.com/b"));
    }

    @Test
    @DisplayName("Blank and whitespace-only loc values are dropped")
    void dropsBlankLocs() throws IOException {
        byte[] body = xml("""
                <urlset>
                  <url><loc>  https://example.com/a  </loc></url>
                  <url><loc>   </loc></url>
                </urlset>
                """);

        DumWCSitemap sitemap = DumWCSitemap.parse(body, BASE);

        assertEquals(1, sitemap.locations().size());
        assertEquals("https://example.com/a", sitemap.locations().get(0));
    }
}
