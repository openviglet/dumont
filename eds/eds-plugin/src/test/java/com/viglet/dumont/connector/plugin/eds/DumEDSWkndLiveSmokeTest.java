/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.plugin.eds;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.viglet.dumont.connector.plugin.eds.discovery.DumEDSPathFilter;
import com.viglet.dumont.connector.plugin.eds.extract.DumEDSBodyExtractor;
import com.viglet.dumont.connector.plugin.eds.extract.DumEDSDocumentMerger;
import com.viglet.dumont.connector.plugin.eds.extract.DumEDSMetadataExtractor;
import com.viglet.dumont.connector.plugin.eds.fetch.DumEDSHttpFetcher;
import com.viglet.dumont.connector.plugin.eds.fetch.DumEDSRateLimiter;

/**
 * End-to-end smoke test against the public Adobe WKND demo site
 * ({@code https://main--wknd--hlxsites.aem.live}). Hits the live site: fetches
 * a couple of pages, runs the full extraction stack, and asserts the pipeline
 * works against a real {@code aem.live} surface.
 *
 * <p>Three concerns covered:
 *
 * <ul>
 *   <li>Metadata extraction yields a non-empty {@code title} and {@code description}
 *       for a known WKND article</li>
 *   <li>The body extractor produces non-empty content for {@code /adventures}</li>
 *   <li>The default WKND {@code excludePatterns} (from the sample seed) drop the
 *       expected non-content paths</li>
 * </ul>
 *
 * <p>Disabled by default — opt in with {@code -Deds.integration.live=true}.
 */
@Tag("integration-live")
@EnabledIfSystemProperty(named = "eds.integration.live", matches = "true")
class DumEDSWkndLiveSmokeTest {

    private static final String BASE = "https://main--wknd--hlxsites.aem.live";
    private static final String UA = "Viglet-Dumont-EDS/1.0 (live-smoke)";

    @Test
    void adventureArticleMetadataExtractsTitleAndDescription() {
        DumEDSHttpFetcher fetcher = new DumEDSHttpFetcher();
        DumEDSMetadataExtractor extractor = new DumEDSMetadataExtractor();
        DumEDSDocumentMerger merger = new DumEDSDocumentMerger();

        DumEDSHttpFetcher.FetchResult result = fetcher.fetch(
                URI.create(BASE + "/adventures/bali-surf-camp"),
                new DumEDSHttpFetcher.Settings(UA, 15_000, 0d, 2),
                new DumEDSRateLimiter(DumEDSRateLimiter.UNLIMITED));

        Map<String, String> html = extractor.extract(result.document());
        // Simulate a sparse JSON row to exercise the merger; HTML wins on overlap.
        Map<String, String> merged = merger.merge(
                Map.of("path", "/adventures/bali-surf-camp"),
                html);

        assertThat(merged.get("title")).isNotBlank();
        assertThat(merged.get("description")).isNotBlank();
    }

    @Test
    void adventuresIndexBodyExtractionProducesNonEmptyText() {
        DumEDSHttpFetcher fetcher = new DumEDSHttpFetcher();
        DumEDSBodyExtractor extractor = new DumEDSBodyExtractor();

        DumEDSHttpFetcher.FetchResult result = fetcher.fetch(
                URI.create(BASE + "/adventures"),
                new DumEDSHttpFetcher.Settings(UA, 15_000, 0d, 2),
                new DumEDSRateLimiter(DumEDSRateLimiter.UNLIMITED));

        String body = extractor.extract(result.document(), "main",
                List.of("header", "nav", "footer", "aside", ".breadcrumb"));

        // WKND `/adventures` is a long-running content page; assert that non-trivial
        // body emerges and that stripped chrome doesn't surface in the output.
        assertThat(body).hasSizeGreaterThan(50);
        assertThat(body.toLowerCase()).doesNotContain("<nav");
        assertThat(body.toLowerCase()).doesNotContain("<footer");
    }

    @Test
    void wkndDefaultExcludePatternsFilterRealNonContentPaths() {
        DumEDSPathFilter filter = new DumEDSPathFilter();
        // Matches the seed in eds-plugin-sample/scripts/wknd/export/wknd.json.
        List<String> patterns = List.of(
                "/drafts/**",
                "/experiments/**",
                "/mystique/**",
                "/block-library/**",
                "/tools/**",
                "/search",
                "/footer",
                "/nav",
                "/fragments/**");

        // Paths that should be filtered out.
        assertThat(filter.isExcluded("/drafts/work-in-progress", patterns)).isTrue();
        assertThat(filter.isExcluded("/experiments/hero-variant-a", patterns)).isTrue();
        assertThat(filter.isExcluded("/block-library/cards", patterns)).isTrue();
        assertThat(filter.isExcluded("/fragments/header", patterns)).isTrue();
        assertThat(filter.isExcluded("/footer", patterns)).isTrue();
        assertThat(filter.isExcluded("/nav", patterns)).isTrue();
        assertThat(filter.isExcluded("/search", patterns)).isTrue();

        // Real WKND content paths must survive.
        assertThat(filter.isExcluded("/", patterns)).isFalse();
        assertThat(filter.isExcluded("/adventures", patterns)).isFalse();
        assertThat(filter.isExcluded("/adventures/bali-surf-camp", patterns)).isFalse();
        assertThat(filter.isExcluded("/magazine", patterns)).isFalse();
        assertThat(filter.isExcluded("/about-us", patterns)).isFalse();
    }
}
