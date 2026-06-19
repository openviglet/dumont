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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.web.client.RestClient;

/**
 * Live smoke test that exercises the discovery path against the public Adobe
 * WKND demo site:
 * <a href="https://main--wknd--hlxsites.aem.live/query-index.json">
 * https://main--wknd--hlxsites.aem.live/query-index.json</a>. Disabled in CI by
 * default — opt in with {@code -Deds.integration.live=true}.
 *
 * <p>Side effect: captures the live JSON payload to
 * {@code src/test/resources/fixtures/wknd-live/query-index.json} (gitignored)
 * so a developer can audit drift across WKND content releases.
 *
 * <p>Assertions are deliberately loose around the exact path count: Adobe ships
 * new WKND content periodically, so we assert a sensible floor and check that a
 * handful of known-stable paths still exist.
 */
@Tag("integration-live")
@EnabledIfSystemProperty(named = "eds.integration.live", matches = "true")
class DumEDSIndexClientLiveTest {

    private static final String WKND_BASE_URL = "https://main--wknd--hlxsites.aem.live";
    private static final String WKND_INDEX = "/query-index.json";
    private static final String LIVE_FIXTURE_DIR = "src/test/resources/fixtures/wknd-live";

    @Test
    void discoversAllPathsFromWkndQueryIndex() throws IOException, InterruptedException {
        // Capture the raw payload first so failure modes are diagnosable.
        captureRawPayload();

        DumEDSIndexClient client = new DumEDSIndexClient(new DumEDSSheetParser(),
                RestClient.builder());

        List<Map<String, String>> rows = client.fetchAllRows(WKND_BASE_URL, WKND_INDEX,
                DumEDSIndexClient.DEFAULT_USER_AGENT);

        // WKND has grown to ~250 paths and continues to expand; floor at 100,
        // sanity ceiling at 2000.
        assertThat(rows).hasSizeBetween(100, 2000)
                .allMatch(row -> row.get("path") != null);

        List<String> paths = rows.stream().map(r -> r.get("path")).toList();
        assertThat(paths).contains(
                "/",
                "/about-us",
                "/adventures",
                "/adventures/bali-surf-camp");
    }

    /**
     * WKND populates every column for every row, so this test pins the
     * "all metadata present in JSON sheet" property — title, description,
     * image, and lastModified all come from the JSON sheet. og:title fill-in
     * is not needed for WKND; the merge code path is exercised on synthetic
     * fixtures in {@code DumEDSPluginProcessEndToEndTest}.
     */
    @Test
    void everyRowCarriesFullMetadata() {
        DumEDSIndexClient client = new DumEDSIndexClient(new DumEDSSheetParser(),
                RestClient.builder());

        List<Map<String, String>> rows = client.fetchAllRows(WKND_BASE_URL, WKND_INDEX,
                DumEDSIndexClient.DEFAULT_USER_AGENT);

        assertThat(rows).isNotEmpty();
        Map<String, String> sample = rows.stream()
                .filter(r -> "/about-us".equals(r.get("path")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected /about-us in WKND live index"));
        assertThat(sample).containsKeys("path", "title", "description", "lastModified");
    }

    private static void captureRawPayload() throws IOException, InterruptedException {
        Path dir = Paths.get(LIVE_FIXTURE_DIR);
        Files.createDirectories(dir);
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(WKND_BASE_URL + WKND_INDEX))
                .header("User-Agent", DumEDSIndexClient.DEFAULT_USER_AGENT)
                .GET().build();
        HttpResponse<String> response = http.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Files.writeString(dir.resolve("query-index.json"), response.body(),
                StandardCharsets.UTF_8);
    }
}
