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
            collection.insertOne(new Document("contentId", "c1").append("status", "INDEXED")
                    .append("resultStatus", "SUCCESS").append("url", "http://site/a")
                    .append("date", new Date()));
            collection.insertOne(new Document("contentId", "c2").append("status", "DEINDEXED")
                    .append("resultStatus", "ERROR").append("url", "http://site/b")
                    .append("date", new Date()));
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
                "desc");
        assertThat(result.get("totalElements")).isEqualTo(2L);
        assertThat((List<?>) result.get("content")).hasSize(2);
    }

    @Test
    void filtersByStatusAndContentId() {
        assertThat(api.indexingLogging(0, 100, null, null, "INDEXED", null, null, null, "asc")
                .get("totalElements")).isEqualTo(1L);
        assertThat(api.indexingLogging(0, 100, null, null, null, "c2", null, null, "desc")
                .get("totalElements")).isEqualTo(1L);
    }

    @Test
    void filtersByResultStatusAndUrlRegex() {
        assertThat(api.indexingLogging(0, 100, null, null, null, null, "ERROR", null, "desc")
                .get("totalElements")).isEqualTo(1L);
        assertThat(api.indexingLogging(0, 100, null, null, null, null, null, "site/a", "desc")
                .get("totalElements")).isEqualTo(1L);
    }

    @Test
    void appliesDateRangeFilter() {
        Map<String, Object> result = api.indexingLogging(0, 100, "2000-01-01", "2100-01-01", null,
                null, null, null, "desc");
        assertThat(result.get("totalElements")).isEqualTo(2L);
    }
}
