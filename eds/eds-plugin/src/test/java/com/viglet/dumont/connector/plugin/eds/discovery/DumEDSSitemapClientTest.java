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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.plugin.eds.testsupport.EmbeddedHttpServer;

class DumEDSSitemapClientTest {

    @Test
    void parsesPlainUrlset() throws IOException {
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            server.route("/sitemap.xml", exchange -> EmbeddedHttpServer.respondBytes(exchange,
                    "application/xml",
                    urlset(server.baseUrl(),
                            "/pt-br/pf",
                            "/pt-br/pj",
                            "/pt-br/sala-de-imprensa")
                            .getBytes(StandardCharsets.UTF_8)));

            DumEDSSitemapClient client = new DumEDSSitemapClient(RestClient.builder());

            Set<String> paths = client.fetchPaths(server.baseUrl(), "/sitemap.xml", "test-ua");

            assertThat(paths).containsExactly("/pt-br/pf", "/pt-br/pj", "/pt-br/sala-de-imprensa");
        }
    }

    @Test
    void decompressesGzippedSitemap() throws IOException {
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            byte[] gz = gzip(urlset(server.baseUrl(), "/foo", "/bar"));
            server.route("/sitemap.xml.gz", exchange -> EmbeddedHttpServer.respondBytes(exchange,
                    "application/gzip", gz));

            DumEDSSitemapClient client = new DumEDSSitemapClient(RestClient.builder());

            Set<String> paths = client.fetchPaths(server.baseUrl(), "/sitemap.xml.gz", "test-ua");

            assertThat(paths).containsExactly("/foo", "/bar");
        }
    }

    @Test
    void detectsGzipByMagicHeaderWhenContentTypeMisleading() throws IOException {
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            byte[] gz = gzip(urlset(server.baseUrl(), "/qux"));
            server.route("/sitemap.xml", exchange -> EmbeddedHttpServer.respondBytes(exchange,
                    "application/xml", gz));

            DumEDSSitemapClient client = new DumEDSSitemapClient(RestClient.builder());

            Set<String> paths = client.fetchPaths(server.baseUrl(), "/sitemap.xml", "test-ua");

            assertThat(paths).containsExactly("/qux");
        }
    }

    @Test
    void recursesIntoSitemapIndex() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            String base = server.baseUrl();
            server.route("/sitemap.xml", exchange -> {
                requests.incrementAndGet();
                String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">"
                        + "<sitemap><loc>" + base + "/sitemap-pf.xml</loc></sitemap>"
                        + "<sitemap><loc>" + base + "/sitemap-pj.xml</loc></sitemap>"
                        + "</sitemapindex>";
                EmbeddedHttpServer.respondBytes(exchange, "application/xml",
                        xml.getBytes(StandardCharsets.UTF_8));
            });
            server.route("/sitemap-pf.xml", exchange -> EmbeddedHttpServer.respondBytes(exchange,
                    "application/xml", urlset(base, "/pt-br/pf", "/pt-br/pf/conta-digital")
                            .getBytes(StandardCharsets.UTF_8)));
            server.route("/sitemap-pj.xml", exchange -> EmbeddedHttpServer.respondBytes(exchange,
                    "application/xml", urlset(base, "/pt-br/pj").getBytes(StandardCharsets.UTF_8)));

            DumEDSSitemapClient client = new DumEDSSitemapClient(RestClient.builder());

            Set<String> paths = client.fetchPaths(server.baseUrl(), "/sitemap.xml", "test-ua");

            assertThat(paths).containsExactly("/pt-br/pf", "/pt-br/pf/conta-digital", "/pt-br/pj");
            assertThat(requests.get()).isEqualTo(1);
        }
    }

    @Test
    void dropsForeignHostUrls() throws IOException {
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">"
                    + "<url><loc>" + server.baseUrl() + "/local</loc></url>"
                    + "<url><loc>https://other.invalid/elsewhere</loc></url>"
                    + "</urlset>";
            server.route("/sitemap.xml", exchange -> EmbeddedHttpServer.respondBytes(exchange,
                    "application/xml", xml.getBytes(StandardCharsets.UTF_8)));

            DumEDSSitemapClient client = new DumEDSSitemapClient(RestClient.builder());

            Set<String> paths = client.fetchPaths(server.baseUrl(), "/sitemap.xml", "test-ua");

            assertThat(paths).containsExactly("/local");
        }
    }

    @Test
    void returnsEmptySetOnHttpFailure() throws IOException {
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            // No routes registered → returns 404.
            DumEDSSitemapClient client = new DumEDSSitemapClient(RestClient.builder());

            Set<String> paths = client.fetchPaths(server.baseUrl(), "/sitemap.xml", "test-ua");

            assertThat(paths).isEmpty();
        }
    }

    private static String urlset(String baseUrl, String... paths) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
        for (String path : paths) {
            sb.append("<url><loc>").append(baseUrl).append(path).append("</loc></url>");
        }
        sb.append("</urlset>");
        return sb.toString();
    }

    private static byte[] gzip(String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(baos)) {
            gz.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }
}
