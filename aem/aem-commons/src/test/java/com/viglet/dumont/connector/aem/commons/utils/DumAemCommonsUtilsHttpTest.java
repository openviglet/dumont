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

package com.viglet.dumont.connector.aem.commons.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;

/**
 * Exercises the real HTTP fetch paths of {@link DumAemCommonsUtils} (infinity/model/depth JSON,
 * cached and uncached body fetch, generic deserialization, publish-environment routing and
 * connection-failure handling) against an in-process JDK {@link HttpServer} on localhost — no Docker
 * or external AEM needed.
 */
class DumAemCommonsUtilsHttpTest {

    private static final String OBJECT_JSON =
            "{\"jcr:content\":{\"jcr:title\":\"WKND\"},\"jcr:primaryType\":\"cq:Page\"}";

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body;
            if (path.contains("notjson")) {
                body = "this is not json at all";
            } else if (path.contains("arr") && !path.contains("jcr:content")) {
                body = "[\"/content/arr/jcr:content\"]";
            } else if (path.contains("generic")) {
                body = "{\"name\":\"wknd\"}";
            } else {
                body = OBJECT_JSON;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private DumAemConfiguration config() {
        return DumAemConfiguration.builder().url(baseUrl).username("admin").password("admin")
                .rootPath("/content/wknd").contentType("cq:Page").build();
    }

    @Test
    void getResponseBodyFetchesUncached() throws IOException {
        Optional<String> body =
                DumAemCommonsUtils.getResponseBody(baseUrl + "/content/wknd.json", config(), false);

        assertThat(body).isPresent();
        assertThat(body.get()).contains("WKND");
    }

    @Test
    void getResponseBodyUsesCacheOnSecondCall() throws IOException {
        String url = baseUrl + "/content/cached.json";

        assertThat(DumAemCommonsUtils.getResponseBody(url, config(), true)).isPresent();
        // Second call resolves from the Caffeine response cache.
        assertThat(DumAemCommonsUtils.getResponseBody(url, config(), true)).isPresent();
    }

    @Test
    void getResponseBodyReturnsEmptyForInvalidJson() throws IOException {
        Optional<String> body = DumAemCommonsUtils
                .getResponseBody(baseUrl + "/content/notjson.json", config(), false);

        assertThat(body).isEmpty();
    }

    @Test
    void getResponseBodyDeserializesToType() throws IOException {
        Optional<Bean> bean = DumAemCommonsUtils.getResponseBody(baseUrl + "/content/generic.json",
                config(), Bean.class, false);

        assertThat(bean).isPresent();
        assertThat(bean.get().name()).isEqualTo("wknd");
    }

    @Test
    void getResponseBodyTypedReturnsEmptyForInvalidJson() throws IOException {
        Optional<Bean> bean = DumAemCommonsUtils.getResponseBody(baseUrl + "/content/notjson.json",
                config(), Bean.class, false);

        assertThat(bean).isEmpty();
    }

    @Test
    void getInfinityJsonReturnsObject() {
        Optional<JSONObject> json =
                DumAemCommonsUtils.getInfinityJson("/content/wknd", config(), false);

        assertThat(json).isPresent();
        assertThat(json.get().getJSONObject("jcr:content").getString("jcr:title")).isEqualTo("WKND");
    }

    @Test
    void getInfinityJsonFollowsArrayToFirstElement() {
        Optional<JSONObject> json =
                DumAemCommonsUtils.getInfinityJson("/content/arr", config(), false);

        assertThat(json).isPresent();
        assertThat(json.get().has("jcr:primaryType")).isTrue();
    }

    @Test
    void getInfinityJsonRoutesToPublishPrefix() {
        DumAemConfiguration publishConfig = config().toBuilder().publishURLPrefix(baseUrl).build();

        Optional<JSONObject> json = DumAemCommonsUtils.getInfinityJson("/content/wknd",
                publishConfig, false, DumAemEnv.PUBLISHING);

        assertThat(json).isPresent();
    }

    @Test
    void getInfinityJsonEmptyOnConnectionFailure() {
        DumAemConfiguration down = DumAemConfiguration.builder().url("http://localhost:1")
                .username("admin").password("admin").build();

        assertThat(DumAemCommonsUtils.getInfinityJson("/content/wknd", down, false)).isEmpty();
    }

    @Test
    void getModelJsonReturnsObject() {
        assertThat(DumAemCommonsUtils.getModelJson("/content/wknd", config())).isPresent();
    }

    @Test
    void getModelJsonEmptyForNonJson() {
        assertThat(DumAemCommonsUtils.getModelJson("/content/notjson", config())).isEmpty();
    }

    @Test
    void getDepthJsonReturnsObject() {
        assertThat(DumAemCommonsUtils.getDepthJson("/content/wknd", config(), 2)).isPresent();
    }

    @Test
    void getDepthJsonFollowsArrayToFirstElement() {
        assertThat(DumAemCommonsUtils.getDepthJson("/content/arr", config(), 1)).isPresent();
    }

    record Bean(String name) {
    }
}
