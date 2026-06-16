/*
 * Copyright (C) 2016-2025 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for roadmap D05 — charset auto-detection. The crawler used to
 * force ISO-8859-1, which mangled UTF-8 pt-BR pages. These tests pin the detection
 * precedence: HTTP {@code Content-Type} header → {@code <meta charset>} / BOM → UTF-8.
 */
@DisplayName("DumWCPluginProcess charset detection (D05)")
class DumWCPluginProcessCharsetTest {

    private static final String BASE_URI = "https://example.com";
    private static final Charset LATIN1 = StandardCharsets.ISO_8859_1;
    // pt-BR text exercising accents representable in both Latin-1 and UTF-8.
    private static final String ACCENTED = "Computação e Informática - Pós-graduação";

    private static byte[] html(String body, Charset charset) {
        return body.getBytes(charset);
    }

    @Test
    @DisplayName("UTF-8 page declared via <meta charset> keeps accents")
    void utf8MetaCharset() throws IOException {
        byte[] body = html(
                "<html><head><meta charset=\"utf-8\"><title>" + ACCENTED + "</title></head>"
                        + "<body><p>" + ACCENTED + "</p></body></html>",
                StandardCharsets.UTF_8);

        Document document = DumWCPluginProcess.parseDocument(body, "text/html", BASE_URI);

        assertEquals(ACCENTED, document.title());
        assertEquals(ACCENTED, document.selectFirst("p").text());
    }

    @Test
    @DisplayName("Latin-1 page declared via Content-Type header keeps accents")
    void latin1ContentTypeHeader() throws IOException {
        byte[] body = html(
                "<html><head><title>" + ACCENTED + "</title></head>"
                        + "<body><p>" + ACCENTED + "</p></body></html>",
                LATIN1);

        Document document = DumWCPluginProcess.parseDocument(body, "text/html; charset=ISO-8859-1", BASE_URI);

        assertEquals(ACCENTED, document.title());
        assertEquals(ACCENTED, document.selectFirst("p").text());
    }

    @Test
    @DisplayName("Latin-1 page declared via legacy <meta http-equiv> keeps accents")
    void latin1MetaHttpEquiv() throws IOException {
        byte[] body = html(
                "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-1\">"
                        + "<title>" + ACCENTED + "</title></head><body><p>" + ACCENTED + "</p></body></html>",
                LATIN1);

        Document document = DumWCPluginProcess.parseDocument(body, "text/html", BASE_URI);

        assertEquals(ACCENTED, document.title());
        assertEquals(ACCENTED, document.selectFirst("p").text());
    }

    @Test
    @DisplayName("Undeclared charset defaults to UTF-8")
    void defaultsToUtf8() throws IOException {
        byte[] body = html(
                "<html><head><title>" + ACCENTED + "</title></head><body><p>" + ACCENTED + "</p></body></html>",
                StandardCharsets.UTF_8);

        Document document = DumWCPluginProcess.parseDocument(body, null, BASE_URI);

        assertEquals(ACCENTED, document.title());
        assertEquals(ACCENTED, document.selectFirst("p").text());
    }

    @Test
    @DisplayName("Header charset wins over a conflicting <meta charset>")
    void headerWinsOverMeta() throws IOException {
        // Body bytes are Latin-1; a stale meta tag claims UTF-8. The header must win.
        byte[] body = html(
                "<html><head><meta charset=\"utf-8\"><title>" + ACCENTED + "</title></head>"
                        + "<body><p>" + ACCENTED + "</p></body></html>",
                LATIN1);

        Document document = DumWCPluginProcess.parseDocument(body, "text/html; charset=ISO-8859-1", BASE_URI);

        assertEquals(ACCENTED, document.title());
    }

    @Test
    @DisplayName("charsetFromContentType parses, trims quotes and rejects unsupported")
    void charsetFromContentType() {
        assertEquals("UTF-8", DumWCPluginProcess.charsetFromContentType("text/html; charset=UTF-8"));
        assertEquals("ISO-8859-1",
                DumWCPluginProcess.charsetFromContentType("text/html;charset=\"ISO-8859-1\""));
        assertNull(DumWCPluginProcess.charsetFromContentType("text/html"));
        assertNull(DumWCPluginProcess.charsetFromContentType(null));
        assertNull(DumWCPluginProcess.charsetFromContentType("text/html; charset=not-a-real-charset"));
    }
}
