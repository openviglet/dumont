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
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.commons.spi.structured.oaipmh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.viglet.dumont.connector.commons.http.DumHttpRetry;
import com.viglet.dumont.connector.commons.text.DumTextExtractor;

/** Reading a record's PDF from the repository's own host (roadmap D315), against an in-process server. */
class DumHttpOaiPmhFullTextFetcherTest {

    private static final String HOST = "127.0.0.1";
    private static final String SENTENCE = "Full text of the deposited article";

    private HttpServer server;
    private final List<String> served = new ArrayList<>();
    private final List<Long> paced = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        byte[] pdf = pdf(SENTENCE);
        server = HttpServer.create(new InetSocketAddress(HOST, 0), 0);
        server.createContext("/bitstream/paper.pdf", exchange -> respond(exchange, 200, "application/pdf", pdf));
        server.createContext("/download/7", exchange -> respond(exchange, 200, "application/pdf; charset=binary", pdf));
        server.createContext("/octet/paper.pdf", exchange -> respond(exchange, 200, "application/octet-stream", pdf));
        server.createContext("/items/7", exchange -> respond(exchange, 200, "text/html",
                "<html>landing</html>".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/moved", exchange -> redirect(exchange, "/bitstream/paper.pdf"));
        server.createContext("/away", exchange -> redirect(exchange,
                "http://localhost:" + server.getAddress().getPort() + "/bitstream/paper.pdf"));
        server.createContext("/missing.pdf", exchange -> respond(exchange, 404, "text/html", new byte[0]));
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void readsAPdfAndAnythingServedAsOne() throws IOException {
        assertTrue(fetcher().text(url("/bitstream/paper.pdf"), HOST).orElseThrow().contains(SENTENCE));
        assertTrue(fetcher().text(url("/download/7"), HOST).orElseThrow().contains(SENTENCE));
        assertTrue(fetcher().text(url("/octet/paper.pdf"), HOST).orElseThrow().contains(SENTENCE),
                "a generic binary from a .pdf path is a PDF");
    }

    @Test
    void aLandingPageIsNotReadAsText() throws IOException {
        assertEquals(Optional.empty(), fetcher().text(url("/items/7"), HOST));
    }

    @Test
    void followsARedirectOnTheSameHostOnly() throws IOException {
        assertTrue(fetcher().text(url("/moved"), HOST).orElseThrow().contains(SENTENCE));

        served.clear();
        assertEquals(Optional.empty(), fetcher().text(url("/away"), HOST));
        assertEquals(List.of("/away"), served, "the redirect to another host is never followed");
    }

    @Test
    void neverFetchesAnotherHost() throws IOException {
        assertEquals(Optional.empty(), fetcher().text(url("/bitstream/paper.pdf"), "repo.example"));
        assertEquals(List.of(), served);
    }

    @Test
    void aPdfOverTheByteCapIsNotRead() throws IOException {
        DumHttpOaiPmhFullTextFetcher capped = new DumHttpOaiPmhFullTextFetcher(client(), retry(), paced::add,
                Duration.ofSeconds(1), DumTextExtractor.builder().maxBytes(100).build());

        assertEquals(Optional.empty(), capped.text(url("/bitstream/paper.pdf"), HOST));
    }

    @Test
    void anErrorStatusFailsTheFetch() {
        IOException e = assertThrows(IOException.class, () -> fetcher().text(url("/missing.pdf"), HOST));
        assertTrue(e.getMessage().contains("HTTP 404"), e.getMessage());
    }

    @Test
    void downloadsArePaced() throws IOException {
        DumHttpOaiPmhFullTextFetcher fetcher = fetcher();
        fetcher.text(url("/bitstream/paper.pdf"), HOST);
        fetcher.text(url("/download/7"), HOST);

        assertEquals(List.of(1_000L, 1_000L), paced, "each download waits its turn at the interval");
    }

    @Test
    void recognisesAPdfByTypeOrByPath() {
        assertTrue(DumHttpOaiPmhFullTextFetcher.isPdf("application/pdf", "/x"));
        assertTrue(DumHttpOaiPmhFullTextFetcher.isPdf("", "/x/Paper.PDF"));
        assertFalse(DumHttpOaiPmhFullTextFetcher.isPdf("text/html", "/x/paper.pdf"));
        assertFalse(DumHttpOaiPmhFullTextFetcher.isPdf("application/octet-stream", "/x/paper"));
        assertTrue(DumHttpOaiPmhFullTextFetcher.onHost(URI.create("HTTPS://Repo.Example/a"), "repo.example"));
        assertFalse(DumHttpOaiPmhFullTextFetcher.onHost(URI.create("ftp://repo.example/a"), "repo.example"));
    }

    private DumHttpOaiPmhFullTextFetcher fetcher() {
        return new DumHttpOaiPmhFullTextFetcher(client(), retry(), paced::add, Duration.ofSeconds(1),
                DumTextExtractor.withDefaults());
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private static DumHttpRetry retry() {
        return new DumHttpRetry(ms -> {
        }, 0);
    }

    private String url(String path) {
        return "http://" + HOST + ":" + server.getAddress().getPort() + path;
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        served.add(exchange.getRequestURI().getPath());
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void respond(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        served.add(exchange.getRequestURI().getPath());
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static byte[] pdf(String text) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText(text);
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
