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

package com.viglet.dumont.connector.plugin.eds.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.eds.commons.domain.DumEDSSheetPage;

class DumEDSSheetParserTest {

    private DumEDSSheetParser parser;

    @BeforeEach
    void setUp() {
        parser = new DumEDSSheetParser();
    }

    @Test
    void parsesEDSShapedFixtureToThirtyFiveRows() throws IOException {
        String json = loadFixture("/fixtures/eds-shape.json");

        DumEDSSheetPage page = parser.parse(json);

        assertThat(page.total()).isEqualTo(35);
        assertThat(page.rows()).hasSize(35);
        assertThat(page.isSingleSheet()).isTrue();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.columns()).containsExactly("path", "lastModified", "robots", "title",
                "date", "description", "image");
    }

    @Test
    void omitsAbsentFieldsFromRowMaps() throws IOException {
        String json = loadFixture("/fixtures/eds-shape.json");

        DumEDSSheetPage page = parser.parse(json);

        Map<String, String> contaDigital = page.rows().stream()
                .filter(r -> "/pt-br/pf/conta-digital".equals(r.get("path")))
                .findFirst().orElseThrow();
        assertThat(contaDigital).containsOnlyKeys("path")
                .doesNotContainKey("title")
                .doesNotContainKey("description");
    }

    @Test
    void treatsEmptyStringValuesAsAbsent() throws IOException {
        String json = loadFixture("/fixtures/eds-shape.json");

        DumEDSSheetPage page = parser.parse(json);

        Map<String, String> imprensa = page.rows().stream()
                .filter(r -> "/pt-br/sala-de-imprensa".equals(r.get("path")))
                .findFirst().orElseThrow();
        assertThat(imprensa).containsKey("title")
                .doesNotContainKey("date")
                .doesNotContainKey("description");
    }

    @Test
    void rejectsNullOrBlankPayloads() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> parser.parse("not a json"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMultiSheetWithDedicatedException() {
        String multiSheet = "{ \":type\": \"multi-sheet\", \":names\": [\"a\",\"b\"], "
                + "\"data\": [] }";

        assertThatThrownBy(() -> parser.parse(multiSheet))
                .isInstanceOf(DumEDSSheetParser.UnsupportedSheetTypeException.class);
    }

    @Test
    void defaultsEnvelopeFieldsWhenAbsent() {
        String minimal = "{ \"data\": [ { \"path\": \"/a\" }, { \"path\": \"/b\" } ] }";

        DumEDSSheetPage page = parser.parse(minimal);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.offset()).isZero();
        assertThat(page.limit()).isEqualTo(2);
        assertThat(page.sheetType()).isEqualTo(DumEDSSheetPage.TYPE_SHEET);
        assertThat(page.columns()).containsExactly("path");
    }

    @Test
    void infersColumnsFromRowsWhenEnvelopeLacksThem() {
        String body = "{ \"data\": [ "
                + "{ \"path\": \"/a\", \"title\": \"A\" }, "
                + "{ \"path\": \"/b\", \"image\": \"x.jpg\" } "
                + "] }";

        DumEDSSheetPage page = parser.parse(body);

        assertThat(page.columns()).contains("path", "title", "image");
    }

    @Test
    void handlesExplicitNullsInRowAsAbsent() {
        String body = "{ \"data\": [ { \"path\": \"/a\", \"title\": null } ] }";

        DumEDSSheetPage page = parser.parse(body);

        assertThat(page.rows()).singleElement()
                .satisfies(row -> {
                    assertThat(row).containsEntry("path", "/a");
                    assertThat(row).doesNotContainKey("title");
                });
    }

    private String loadFixture(String resourcePath) throws IOException {
        try (var in = getClass().getResourceAsStream(resourcePath)) {
            assertThat(in).as("fixture %s present", resourcePath).isNotNull();
            return IOUtils.toString(in, StandardCharsets.UTF_8);
        }
    }
}
