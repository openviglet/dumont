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

package com.viglet.dumont.connector.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * What the indexing log can be asked (roadmap D72).
 *
 * <p>
 * Both backends express the same predicate in different languages, so both are asserted here:
 * Mongo's as the BSON it will send, Redis's as the row-by-row match it performs in Java. Neither
 * needs a server, because in both cases the filter is a pure function of the query string.
 */
class DumLoggingAPITest {

    private static String rendered(Bson filter) {
        return filter.toBsonDocument(BsonDocument.class,
                com.mongodb.MongoClientSettings.getDefaultCodecRegistry()).toJson();
    }

    private static Bson filterOn(String source, String transactionId) {
        return DumLoggingAPI.indexingFilter(null, null, null, null, null, null, source,
                transactionId);
    }

    private static JsonNode row(String json) {
        return new ObjectMapper().readTree(json);
    }

    @Test
    @DisplayName("Mongo can be asked what one source did and what one run did")
    void mongoFiltersOnSourceAndRun() {
        String filter = rendered(filterOn("wknd", "tx-1"));

        assertTrue(filter.contains("\"source\""), filter);
        assertTrue(filter.contains("wknd"), filter);
        assertTrue(filter.contains("\"transactionId\""), filter);
        assertTrue(filter.contains("tx-1"), filter);
    }

    @Test
    @DisplayName("an absent filter contributes no clause, so no filter means every row")
    void anAbsentFilterMatchesEverything() {
        assertEquals("{}", rendered(filterOn(null, null)));
        assertEquals("{}", rendered(filterOn("  ", "")));

        // And naming only one of the two does not smuggle in a clause for the other.
        String onlySource = rendered(filterOn("wknd", null));
        assertTrue(onlySource.contains("\"source\""), onlySource);
        assertFalse(onlySource.contains("transactionId"), onlySource);
    }

    @Test
    @DisplayName("the five filters that were already there still build")
    void theOlderFiltersAreUnchanged() {
        String filter = rendered(DumLoggingAPI.indexingFilter("2026-01-01", "2026-01-31",
                "INDEXED", "/content/a", "SUCCESS", "example", null, null));

        assertTrue(filter.contains("\"status\""), filter);
        assertTrue(filter.contains("\"contentId\""), filter);
        assertTrue(filter.contains("\"resultStatus\""), filter);
        assertTrue(filter.contains("\"url\""), filter);
        assertTrue(filter.contains("\"date\""), filter);
    }

    @Test
    @DisplayName("on Redis, a row matches a filter naming its own value")
    void redisMatchesOnValue() {
        JsonNode wknd = row("{\"contentId\":\"/a\",\"source\":\"wknd\",\"transactionId\":\"tx-1\"}");

        assertTrue(DumLoggingAPI.matches(wknd, "source", "wknd"));
        assertTrue(DumLoggingAPI.matches(wknd, "transactionId", "tx-1"));
        assertFalse(DumLoggingAPI.matches(wknd, "source", "docs"));
    }

    @Test
    @DisplayName("an unattributed row survives an unfiltered listing but does not answer a filter")
    void anUnattributedRowIsNotHidden() {
        // Rows written before the trail existed (D71) carry no source. Dropping them from a
        // listing nobody filtered would hide the history a reader came to read.
        JsonNode old = row("{\"contentId\":\"/a\"}");
        JsonNode nulled = row("{\"contentId\":\"/a\",\"source\":null}");

        assertTrue(DumLoggingAPI.matches(old, "source", null), "no filter must match every row");
        assertTrue(DumLoggingAPI.matches(old, "source", "   "), "a blank filter is no filter");
        assertTrue(DumLoggingAPI.matches(nulled, "source", null));

        // But it is not evidence about any particular source, so it does not answer one.
        assertFalse(DumLoggingAPI.matches(old, "source", "wknd"));
        assertFalse(DumLoggingAPI.matches(nulled, "source", "wknd"));
    }
}
