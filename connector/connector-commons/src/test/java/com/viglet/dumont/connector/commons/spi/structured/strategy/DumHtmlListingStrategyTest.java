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

/** Unit test for {@link DumHtmlListingStrategy} (roadmap D02): anchors under a selector -> detail URLs. */
class DumHtmlListingStrategyTest {

    private static final String LISTING = "https://uni.edu/cursos";

    private static final Map<String, String> PAGES = Map.of(LISTING, """
            <html><body>
              <ul class="catalog">
                <li><a class="card" href="/curso/algoritmos">Algoritmos</a></li>
                <li><a class="card" href="/curso/calculo">Cálculo</a></li>
                <li><a class="card" href="/curso/algoritmos">Algoritmos (dup)</a></li>
                <li><a class="card" href="mailto:info@uni.edu">Contato</a></li>
                <li><a class="card" href="javascript:void(0)">Toggle</a></li>
                <li><a class="card" href="#top">Topo</a></li>
              </ul>
              <a class="nav" href="/sobre">Sobre</a>
            </body></html>
            """);

    private final DumHtmlFetcher fetcher = DumHtmlFetcher.ofPages(PAGES);

    @Test
    void resolvesDetailAnchorsUnderSelectorWithLevel() {
        DumHtmlListingStrategy strategy = DumHtmlListingStrategy.builder(fetcher)
                .source(LISTING, "a.card", "undergraduate")
                .build();

        List<DumDiscoveredUrl> discovered = strategy.discoverWithLevels();

        // Absolute URLs, deduplicated, with mailto/javascript/# filtered out; nav anchor excluded.
        assertEquals(List.of(
                new DumDiscoveredUrl("https://uni.edu/curso/algoritmos", "undergraduate"),
                new DumDiscoveredUrl("https://uni.edu/curso/calculo", "undergraduate")),
                discovered);
        assertFalse(strategy.discover().contains("https://uni.edu/sobre"));
        assertTrue(strategy.discover().stream().noneMatch(url -> url.startsWith("mailto")));
    }
}
