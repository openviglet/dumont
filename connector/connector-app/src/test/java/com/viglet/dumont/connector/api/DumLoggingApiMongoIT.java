/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.viglet.dumont.connector.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;

/**
 * Integration test for {@link DumLoggingAPI}'s MongoDB engine, backed by a real Mongo via
 * Testcontainers. Exercises the query, pagination and filter paths that a unit test cannot reach
 * without a live store.
 */
@Testcontainers(disabledWithoutDocker = true)
class DumLoggingApiMongoIT {

    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    private static final String DB = "dumontLog";
    private static final String COLLECTION = "indexing";

    private DumLoggingAPI api;

    @BeforeEach
    void seed() {
        try (MongoClient client = MongoClients.create(MONGO.getConnectionString())) {
            MongoCollection<Document> collection =
                    client.getDatabase(DB).getCollection(COLLECTION);
            collection.drop();
            Date fixedDate = new Date(1_700_000_000_000L);
            // c1 carries the trail D71 put on every row; c2 is shaped like a row written before
            // it existed, which is what the unattributed-row rule below is about.
            collection.insertOne(new Document("contentId", "c1").append("status", "INDEXED")
                    .append("resultStatus", "SUCCESS").append("url", "http://site/a")
                    .append("source", "wknd").append("transactionId", "tx-1")
                    .append("date", fixedDate));
            collection.insertOne(new Document("contentId", "c2").append("status", "DEINDEXED")
                    .append("resultStatus", "ERROR").append("url", "http://site/b")
                    .append("date", fixedDate));
        }
        api = new DumLoggingAPI("mongodb", true, MONGO.getConnectionString(), DB, COLLECTION,
                false, "redis://localhost:6379/0", "dumontLog:indexing");
    }

    @Test
    void statusReportsMongoEnabled() {
        assertThat(api.status()).containsEntry("engine", "mongodb").containsEntry("enabled", true);
    }

    @Test
    void returnsAllDocuments() {
        Map<String, Object> result = api.indexingLogging(0, 100, null, null, null, null, null, null,
                null, null, "desc");
        assertThat(result).containsEntry("totalElements", 2L);
        assertThat((List<?>) result.get("content")).hasSize(2);
    }

    @Test
    void filtersByStatusAndContentId() {
        assertThat(api.indexingLogging(0, 100, null, null, "INDEXED", null, null, null, null, null,
                "asc")).containsEntry("totalElements", 1L);
        assertThat(api.indexingLogging(0, 100, null, null, null, "c2", null, null, null, null,
                "desc")).containsEntry("totalElements", 1L);
    }

    @Test
    void filtersByResultStatusAndUrlRegex() {
        assertThat(api.indexingLogging(0, 100, null, null, null, null, "ERROR", null, null, null,
                "desc")).containsEntry("totalElements", 1L);
        assertThat(api.indexingLogging(0, 100, null, null, null, null, null, "site/a", null, null,
                "desc")).containsEntry("totalElements", 1L);
    }

    @Test
    void appliesDateRangeFilter() {
        Map<String, Object> result = api.indexingLogging(0, 100, "2000-01-01", "2100-01-01", null,
                null, null, null, null, null, "desc");
        assertThat(result).containsEntry("totalElements", 2L);
    }

    @Test
    void answersWhatOneSourceDidAndWhatOneRunDid() {
        // The two questions D69 and D71 made the rows capable of answering (D72).
        assertThat(api.indexingLogging(0, 100, null, null, null, null, null, null, "wknd", null,
                "desc")).containsEntry("totalElements", 1L);
        assertThat(api.indexingLogging(0, 100, null, null, null, null, null, null, null, "tx-1",
                "desc")).containsEntry("totalElements", 1L);
        // A source that published nothing here answers with nothing, not with everything.
        assertThat(api.indexingLogging(0, 100, null, null, null, null, null, null, "docs", null,
                "desc")).containsEntry("totalElements", 0L);
    }

    @Test
    void doesNotHideARowWrittenBeforeTheTrailExisted() {
        // c2 has no source. It must still appear in a listing nobody filtered — an unattributed
        // row is a fact about an old message, not a reason to hide the history.
        Map<String, Object> unfiltered = api.indexingLogging(0, 100, null, null, null, null, null,
                null, null, null, "desc");
        assertThat(unfiltered).containsEntry("totalElements", 2L);

        // But it is not evidence about any particular source, so it does not answer one.
        assertThat(api.indexingLogging(0, 100, null, null, null, null, null, null, "wknd", null,
                "desc")).containsEntry("totalElements", 1L);
    }
}
