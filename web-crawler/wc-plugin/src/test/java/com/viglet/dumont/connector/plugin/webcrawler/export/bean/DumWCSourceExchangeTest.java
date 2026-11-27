package com.viglet.dumont.connector.plugin.webcrawler.export.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

/** Roadmap DU17: a web-crawler source that leaves its lists out reads with them empty. */
class DumWCSourceExchangeTest {

    @Test
    @DisplayName("collections missing from the file read as empty, not null")
    void missingCollectionsReadAsEmpty() {
        DumWCSourceExchange source = new ObjectMapper()
                .readValue("{ \"title\": \"Viglet site\", \"url\": \"https://viglet.com\" }", DumWCSourceExchange.class);

        assertEquals("Viglet site", source.getTitle());
        assertTrue(source.getTurSNSites().isEmpty());
        assertTrue(source.getStartingPoints().isEmpty());
        assertTrue(source.getAllowUrls().isEmpty());
        assertTrue(source.getNotAllowUrls().isEmpty());
        assertTrue(source.getNotAllowExtensions().isEmpty());
        assertTrue(source.getAttributes().isEmpty());
    }
}
