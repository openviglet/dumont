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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DumEDSDocumentMergerTest {

    private final DumEDSDocumentMerger merger = new DumEDSDocumentMerger();

    @Test
    void htmlValueOverridesJsonValueOnConflict() {
        Map<String, String> json = Map.of("title", "Old title from sheet");
        Map<String, String> html = Map.of("title", "Fresh title from HTML");

        Map<String, String> merged = merger.merge(json, html);

        assertThat(merged).containsEntry("title", "Fresh title from HTML");
    }

    @Test
    void htmlFillsGapsWhenJsonHasNothing() {
        Map<String, String> json = Map.of("path", "/pt-br/pf/conta-digital");
        Map<String, String> html = Map.of("title", "Conta Digital",
                "description", "Your example account");

        Map<String, String> merged = merger.merge(json, html);

        assertThat(merged)
                .containsEntry("path", "/pt-br/pf/conta-digital")
                .containsEntry("title", "Conta Digital")
                .containsEntry("description", "Your example account");
    }

    @Test
    void jsonOnlyKeysAreKept() {
        Map<String, String> json = Map.of("path", "/x", "robots", "index,follow",
                "customField", "value");
        Map<String, String> html = Map.of();

        Map<String, String> merged = merger.merge(json, html);

        assertThat(merged)
                .containsEntry("path", "/x")
                .containsEntry("robots", "index,follow")
                .containsEntry("customField", "value");
    }

    @Test
    void blankHtmlValueDoesNotDisplaceJsonValue() {
        Map<String, String> json = Map.of("title", "From JSON");
        Map<String, String> html = new HashMap<>();
        html.put("title", "   ");

        Map<String, String> merged = merger.merge(json, html);

        assertThat(merged).containsEntry("title", "From JSON");
    }

    @Test
    void handlesNullInputsSafely() {
        assertThat(merger.merge(null, null)).isEmpty();
        assertThat(merger.merge(Map.of("a", "1"), null)).containsEntry("a", "1");
        assertThat(merger.merge(null, Map.of("a", "1"))).containsEntry("a", "1");
    }
}
