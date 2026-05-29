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

package com.viglet.dumont.connector.plugin.eds.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class DumEDSMetadataExtractorTest {

    private final DumEDSMetadataExtractor extractor = new DumEDSMetadataExtractor();

    @Test
    void readsTitleFromTitleTag() {
        Document doc = Jsoup.parse("<html><head><title>Hello</title></head><body></body></html>");

        Map<String, String> out = extractor.extract(doc);

        assertThat(out).containsEntry("title", "Hello");
    }

    @Test
    void ogTitleOverridesTitleTag() {
        Document doc = Jsoup.parse("<html><head>"
                + "<title>Boring fallback</title>"
                + "<meta property=\"og:title\" content=\"Conta Digital\">"
                + "</head><body></body></html>");

        Map<String, String> out = extractor.extract(doc);

        assertThat(out).containsEntry("title", "Conta Digital");
    }

    @Test
    void picksUpDescriptionAndImageFromOpenGraph() {
        Document doc = Jsoup.parse("<html><head>"
                + "<meta property=\"og:description\" content=\"Your example account\">"
                + "<meta property=\"og:image\" content=\"https://example.invalid/img.webp\">"
                + "</head></html>");

        Map<String, String> out = extractor.extract(doc);

        assertThat(out)
                .containsEntry("description", "Your example account")
                .containsEntry("image", "https://example.invalid/img.webp");
    }

    @Test
    void readsLastModifiedFromMetaModifiedTime() {
        Document doc = Jsoup.parse("<html><head>"
                + "<meta name=\"modified-time\" content=\"2026-05-01T12:34:56Z\">"
                + "</head></html>");

        Map<String, String> out = extractor.extract(doc);

        assertThat(out).containsEntry("lastModified", "2026-05-01T12:34:56Z");
    }

    @Test
    void ignoresEmptyOrWhitespaceContent() {
        Document doc = Jsoup.parse("<html><head>"
                + "<meta property=\"og:title\" content=\"\">"
                + "<meta property=\"og:description\" content=\"   \">"
                + "</head></html>");

        Map<String, String> out = extractor.extract(doc);

        assertThat(out).doesNotContainKeys("title", "description");
    }

    @Test
    void returnsEmptyMapForNullDocument() {
        assertThat(extractor.extract(null)).isEmpty();
    }
}
