/*
 * Copyright (C) 2016-2025 the original author or authors.
 */
package com.viglet.dumont.connector.webcrawler.sample.ext;

import static org.junit.jupiter.api.Assertions.*;

import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.dumont.connector.webcrawler.commons.DumWCContext;
import com.viglet.dumont.connector.webcrawler.commons.ext.DumWCExtInterface;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

@DisplayName("DumWCExtWikipediaTitle Tests")
class DumWCExtWikipediaTitleTest {

    private DumWCExtWikipediaTitle ext;

    @BeforeEach
    void setUp() {
        ext = new DumWCExtWikipediaTitle();
    }

    @Test
    @DisplayName("Should strip Wikipedia suffix from title")
    void shouldStripSuffix() {
        Document doc = Jsoup.parse("<html><head><title>Java (programming language) - Wikipedia</title></head></html>");
        DumWCContext context = DumWCContext.builder().document(doc).url("https://en.wikipedia.org").build();
        Optional<TurMultiValue> result = ext.consume(context);
        assertTrue(result.isPresent());
        assertEquals("Java (programming language)", result.get().get(0));
    }

    @Test
    @DisplayName("Should return full title when no suffix")
    void shouldReturnFullTitle() {
        Document doc = Jsoup.parse("<html><head><title>Some Other Site</title></head></html>");
        DumWCContext context = DumWCContext.builder().document(doc).url("https://example.com").build();
        Optional<TurMultiValue> result = ext.consume(context);
        assertTrue(result.isPresent());
        assertEquals("Some Other Site", result.get().get(0));
    }

    @Test
    @DisplayName("Should implement DumWCExtInterface")
    void shouldImplementInterface() {
        assertTrue(ext instanceof DumWCExtInterface);
    }
}
