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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.plugin.eds.testsupport.EmbeddedHttpServer;

class DumEDSIndexClientPaginationTest {

    private static final String INDEX_PATH = "/query-index.json";
    private static final String USER_AGENT = "test-ua";
    private static final String PATH_PF = "/pt-br/pf";
    private static final String FIELD_PATH = "path";

    @Test
    void paginatesUntilTotalReachedAndAggregatesRows() throws IOException {
        AtomicInteger pageCalls = new AtomicInteger();
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            server.route(INDEX_PATH, exchange -> {
                int offset = parseQueryParam(exchange.getRequestURI(), "offset", 0);
                pageCalls.incrementAndGet();
                EmbeddedHttpServer.respondJson(exchange, pageJson(offset));
            });

            DumEDSIndexClient client = new DumEDSIndexClient(new DumEDSSheetParser(),
                    RestClient.builder());

            List<Map<String, String>> rows = client.fetchAllRows(server.baseUrl(),
                    INDEX_PATH, USER_AGENT, 100);

            assertThat(rows).hasSize(250);
            assertThat(pageCalls.get()).isEqualTo(3);
            assertThat(rows.get(0)).containsEntry(FIELD_PATH, "/p0");
            assertThat(rows.get(249)).containsEntry(FIELD_PATH, "/p249");
        }
    }

    @Test
    void stopsImmediatelyOnEmptyPage() throws IOException {
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            server.route(INDEX_PATH, exchange -> EmbeddedHttpServer.respondJson(exchange,
                    "{ \"total\": 0, \"offset\": 0, \"limit\": 100, "
                            + "\"columns\": [\"path\"], \"data\": [], \":type\": \"sheet\" }"));

            DumEDSIndexClient client = new DumEDSIndexClient(new DumEDSSheetParser(),
                    RestClient.builder());

            List<Map<String, String>> rows = client.fetchAllRows(server.baseUrl(),
                    INDEX_PATH, USER_AGENT, 100);

            assertThat(rows).isEmpty();
        }
    }

    @Test
    void deduplicatesByPathAcrossMultipleIndexes() throws IOException {
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            server.route("/pf-index.json", exchange -> EmbeddedHttpServer.respondJson(exchange,
                    sheet(List.of(
                            row(PATH_PF, "Title PF"),
                            row("/pt-br/pf/conta-digital", null)))));
            server.route("/pj-index.json", exchange -> EmbeddedHttpServer.respondJson(exchange,
                    sheet(List.of(
                            row("/pt-br/pj", "Title PJ"),
                            // intentional duplicate of pf entry
                            row(PATH_PF, "Should be ignored - first one wins")))));

            DumEDSIndexClient client = new DumEDSIndexClient(new DumEDSSheetParser(),
                    RestClient.builder());

            Map<String, Map<String, String>> byPath = client.fetchAllByPath(server.baseUrl(),
                    List.of("/pf-index.json", "/pj-index.json"), USER_AGENT);

            assertThat(byPath).containsOnlyKeys(PATH_PF, "/pt-br/pf/conta-digital", "/pt-br/pj");
            // Subsequent occurrence wins per DumEDSIndexClient.fetchAllByPath contract.
            assertThat(byPath.get(PATH_PF))
                    .containsEntry("title", "Should be ignored - first one wins");
        }
    }

    private static int parseQueryParam(URI uri, String name, int defaultValue) {
        String query = uri.getRawQuery();
        if (query == null) {
            return defaultValue;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            if (pair.substring(0, eq).equals(name)) {
                try {
                    return Integer.parseInt(pair.substring(eq + 1));
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    private static String pageJson(int offset) {
        int total = 250;
        int limit = 100;
        int end = Math.min(offset + limit, total);
        List<String> entries = new ArrayList<>();
        for (int i = offset; i < end; i++) {
            entries.add("{ \"path\": \"/p" + i + "\" }");
        }
        return "{ \"total\": " + total + ", \"offset\": " + offset + ", \"limit\": " + limit
                + ", \"columns\": [\"path\"], \"data\": [" + String.join(", ", entries) + "], "
                + "\":type\": \"sheet\" }";
    }

    private static String sheet(List<String> rows) {
        return "{ \"total\": " + rows.size() + ", \"offset\": 0, \"limit\": " + rows.size()
                + ", \"columns\": [\"path\",\"title\"], \"data\": [" + String.join(", ", rows)
                + "], \":type\": \"sheet\" }";
    }

    private static String row(String path, String title) {
        if (title == null) {
            return "{ \"path\": \"" + path + "\" }";
        }
        return "{ \"path\": \"" + path + "\", \"title\": \"" + title + "\" }";
    }
}
