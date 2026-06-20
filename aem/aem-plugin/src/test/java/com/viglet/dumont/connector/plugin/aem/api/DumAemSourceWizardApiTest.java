/*
 * Copyright (C) 2016-2026 the original author or authors.
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

package com.viglet.dumont.connector.plugin.aem.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.ResponseEntity;

import com.sun.net.httpserver.HttpServer;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.dumont.connector.plugin.aem.service.DumAemExtractorScannerService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemExtractorScannerService.ExtractorClass;

class DumAemSourceWizardApiTest {

    private final DumAemExtractorScannerService scanner =
            mock(DumAemExtractorScannerService.class);
    private final DumAemSourceWizardApi api = new DumAemSourceWizardApi(scanner);

    private HttpServer server;
    private int responseStatus = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/content.1.json", exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Test
    void listExtractorsDelegatesToScanner() {
        when(scanner.list("attribute", "title")).thenReturn(List.of());
        assertThat(api.listExtractors("attribute", "title")).isEmpty();
    }

    @Test
    void testConnectionSucceedsOn200() {
        DumAemConnectionRequest request = DumAemConnectionRequest.builder()
                .endpoint(baseUrl()).username("admin").password("admin").build();

        ResponseEntity<Map<String, Object>> response = api.testConnection(request);

        assertThat(response.getBody()).containsEntry("success", true);
    }

    @Test
    void testConnectionReportsNon200() {
        responseStatus = 401;
        DumAemConnectionRequest request = DumAemConnectionRequest.builder()
                .endpoint(baseUrl()).username("admin").password("bad").build();

        ResponseEntity<Map<String, Object>> response = api.testConnection(request);

        assertThat(response.getBody()).containsEntry("success", false);
        assertThat(response.getBody().get("message").toString()).contains("401");
    }

    @Test
    void browseReturnsTypedChildrenFromDepthJson() {
        JSONObject child = new JSONObject();
        child.put("jcr:primaryType", "cq:Page");
        JSONObject tree = new JSONObject();
        tree.put("en", child);          // typed -> kept
        tree.put("jcr:title", "Home");  // scalar -> skipped
        DumAemBrowseRequest request = DumAemBrowseRequest.builder().endpoint(baseUrl())
                .username("admin").password("admin").path("/content").build();

        try (MockedStatic<DumAemCommonsUtils> mocked = mockStatic(DumAemCommonsUtils.class)) {
            mocked.when(() -> DumAemCommonsUtils.getDepthJson(eq("/content"), any(), eq(1)))
                    .thenReturn(Optional.of(tree));

            ResponseEntity<Map<String, Object>> response = api.browse(request);

            assertThat(response.getBody()).containsEntry("success", true);
            @SuppressWarnings("unchecked")
            List<Map<String, String>> children =
                    (List<Map<String, String>>) response.getBody().get("children");
            assertThat(children).singleElement()
                    .satisfies(c -> assertThat(c).containsEntry("path", "/content/en"));
        }
    }

    @Test
    void browseReturnsFailureWhenNoJson() {
        DumAemBrowseRequest request = DumAemBrowseRequest.builder().endpoint(baseUrl())
                .username("admin").password("admin").path("/content").build();

        try (MockedStatic<DumAemCommonsUtils> mocked = mockStatic(DumAemCommonsUtils.class)) {
            mocked.when(() -> DumAemCommonsUtils.getDepthJson(eq("/content"), any(), eq(1)))
                    .thenReturn(Optional.empty());

            assertThat(api.browse(request).getBody()).containsEntry("success", false);
        }
    }

    @Test
    void testConnectionReturnsFailureWhenUnreachable() {
        DumAemConnectionRequest request = DumAemConnectionRequest.builder()
                .endpoint("http://localhost:1").username("admin").password("admin").build();

        ResponseEntity<Map<String, Object>> response = api.testConnection(request);

        assertThat(response.getBody()).containsEntry("success", false);
    }

    @Test
    void browseReturnsFailureWhenUnreachable() {
        DumAemBrowseRequest request = DumAemBrowseRequest.builder()
                .endpoint("http://localhost:1").username("admin").password("admin")
                .path("/content").build();

        ResponseEntity<Map<String, Object>> response = api.browse(request);

        assertThat(response.getBody()).containsEntry("success", false);
    }
}
