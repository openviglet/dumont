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
 */

package com.viglet.dumont.connector.commons.spi.structured.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Unit test for {@link DumSitemapStrategy} (roadmap D02): index following + URL-level classification. */
class DumSitemapStrategyTest {

    private static final String INDEX = "https://uni.edu/sitemap.xml";
    private static final String GRAD = "https://uni.edu/sitemap-grad.xml";
    private static final String POS = "https://uni.edu/sitemap-pos.xml";

    private static final Map<String, String> PAGES = Map.of(
            INDEX, """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                      <sitemap><loc>https://uni.edu/sitemap-grad.xml</loc></sitemap>
                      <sitemap><loc>https://uni.edu/sitemap-pos.xml</loc></sitemap>
                    </sitemapindex>
                    """,
            GRAD, """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                      <url><loc>https://uni.edu/graduacao/algoritmos</loc></url>
                      <url><loc>https://uni.edu/graduacao/calculo</loc></url>
                      <url><loc>https://uni.edu/sobre</loc></url>
                    </urlset>
                    """,
            POS, """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                      <url><loc>https://uni.edu/pos/mestrado-cc</loc></url>
                    </urlset>
                    """);

    private final DumHtmlFetcher fetcher = DumHtmlFetcher.ofPages(PAGES);

    @Test
    void followsIndexAndClassifiesByPattern() {
        DumSitemapStrategy strategy = DumSitemapStrategy.builder(fetcher)
                .sitemap(INDEX)
                .level("/graduacao/", "undergraduate")
                .level("/pos/", "graduate")
                .build();

        List<DumDiscoveredUrl> discovered = strategy.discoverWithLevels();

        // "/sobre" matches no rule -> dropped. The other three are classified.
        assertEquals(3, discovered.size());
        assertTrue(discovered.contains(
                new DumDiscoveredUrl("https://uni.edu/graduacao/algoritmos", "undergraduate")));
        assertTrue(discovered.contains(
                new DumDiscoveredUrl("https://uni.edu/pos/mestrado-cc", "graduate")));
        assertFalse(strategy.discover().contains("https://uni.edu/sobre"));
    }

    @Test
    void restrictsToIncludedLevels() {
        DumSitemapStrategy strategy = DumSitemapStrategy.builder(fetcher)
                .sitemap(INDEX)
                .level("/graduacao/", "undergraduate")
                .level("/pos/", "graduate")
                .includeLevels("undergraduate")
                .build();

        assertEquals(
                List.of("https://uni.edu/graduacao/algoritmos", "https://uni.edu/graduacao/calculo"),
                strategy.discover());
    }

    @Test
    void withoutRulesYieldsEveryLoc() {
        DumSitemapStrategy strategy = DumSitemapStrategy.builder(fetcher)
                .sitemap(GRAD)
                .build();

        assertEquals(3, strategy.discover().size());
        assertTrue(strategy.discover().contains("https://uni.edu/sobre"));
        assertTrue(strategy.classify("https://uni.edu/anything").isEmpty());
    }
}
