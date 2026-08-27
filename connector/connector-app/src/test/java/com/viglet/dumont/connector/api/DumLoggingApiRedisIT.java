/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.viglet.dumont.connector.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.viglet.dumont.commons.indexing.DumIndexingStatus;
import com.viglet.dumont.commons.indexing.DumLoggingStatus;
import com.viglet.dumont.commons.logging.DumLoggingIndexing;

import redis.clients.jedis.Jedis;
import tools.jackson.databind.json.JsonMapper;

/**
 * Integration test for {@link DumLoggingAPI}'s Redis engine, backed by a real Redis via
 * Testcontainers. Exercises the list read, contentId filter, sort and pagination paths.
 */
@Testcontainers(disabledWithoutDocker = true)
class DumLoggingApiRedisIT {

    private static final String KEY = "dumontLog:indexing";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Instant SEEDED_AT = Instant.ofEpochMilli(1_700_000_000_000L);

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private DumLoggingAPI api;

    private String redisUri() {
        return "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379) + "/0";
    }

    @BeforeEach
    void seed() {
        try (Jedis jedis = new Jedis(REDIS.getHost(), REDIS.getMappedPort(6379))) {
            jedis.del(KEY);
            // Seeded the way DumRedisIndexingAppender actually writes a row — Jackson over a
            // DumLoggingIndexing — rather than by hand. Hand-written rows had no date, which is
            // how a serializer emitting `ISODate("…")` made every query return an empty page
            // without a single test noticing (D73).
            jedis.lpush(KEY, MAPPER.writeValueAsString(DumLoggingIndexing.builder()
                    .contentId("c1").status(DumIndexingStatus.INDEXED)
                    .resultStatus(DumLoggingStatus.SUCCESS).url("http://site/a")
                    .source("wknd").transactionId("tx-1").date(SEEDED_AT).build()));
            jedis.lpush(KEY, MAPPER.writeValueAsString(DumLoggingIndexing.builder()
                    .contentId("c2").status(DumIndexingStatus.DEINDEXED)
                    .resultStatus(DumLoggingStatus.ERROR).url("http://site/b")
                    .date(SEEDED_AT).build()));
        }
        api = new DumLoggingAPI("redis", false, "mongodb://localhost:27017", "dumontLog",
                "indexing", true, redisUri(), KEY);
    }

    @Test
    void statusReportsRedisEnabled() {
        assertThat(api.status()).containsEntry("engine", "redis").containsEntry("enabled", true);
    }

    @Test
    void returnsAllEntries() {
        Map<String, Object> result = api.indexingLogging(0, 100, null, null, null, null, null, null,
                null, null, "desc");
        assertThat(result).containsEntry("totalElements", 2L);
        assertThat((List<?>) result.get("content")).hasSize(2);
    }

    @Test
    void filtersByContentIdAndSortsAscending() {
        Map<String, Object> result = api.indexingLogging(0, 100, null, null, null, "c1", null, null,
                null, null, "asc");
        assertThat(result).containsEntry("totalElements", 1L);
    }

    @Test
    void paginatesResults() {
        Map<String, Object> page0 = api.indexingLogging(0, 1, null, null, null, null, null, null,
                null, null, "desc");
        assertThat((List<?>) page0.get("content")).hasSize(1);
        assertThat(page0).containsEntry("totalElements", 2L);
    }

    @Test
    void answersWhatOneSourceDidAndWhatOneRunDid() {
        // The same two questions the Mongo path answers (D72) — this path scans in Java, so it is
        // asserted separately rather than assumed to follow.
        assertThat(api.indexingLogging(0, 100, null, null, null, null, null, null, "wknd", null,
                "desc")).containsEntry("totalElements", 1L);
        assertThat(api.indexingLogging(0, 100, null, null, null, null, null, null, null, "tx-1",
                "desc")).containsEntry("totalElements", 1L);
        assertThat(api.indexingLogging(0, 100, null, null, null, null, null, null, "docs", null,
                "desc")).containsEntry("totalElements", 0L);
    }

    @Test
    void doesNotHideARowWrittenBeforeTheTrailExisted() {
        // c2 has no source, and must survive a listing nobody filtered.
        assertThat(api.indexingLogging(0, 100, null, null, null, null, null, null, null, null,
                "desc")).containsEntry("totalElements", 2L);
        assertThat(api.indexingLogging(0, 100, null, null, null, null, null, null, "wknd", null,
                "desc")).containsEntry("totalElements", 1L);
    }

    @Test
    @DisplayName("a row left over from before D73 costs its own row and nothing else (D74)")
    void oneUnreadableRowDoesNotEmptyTheLog() {
        try (Jedis jedis = new Jedis(REDIS.getHost(), REDIS.getMappedPort(6379))) {
            // Exactly what an upgraded connector finds still sitting in its list: the Mongo shell
            // notation the serializer used to emit, which no JSON reader accepts.
            jedis.lpush(KEY, "{\"contentId\":\"c0\",\"date\":ISODate(\"2023-11-14T22:13:20Z\")}");
        }

        Map<String, Object> result = api.indexingLogging(0, 100, null, null, null, null, null, null,
                null, null, "desc");

        // The two good rows survive — they used to be discarded along with the bad one.
        assertThat(result).containsEntry("totalElements", 2L);
        assertThat((List<?>) result.get("content")).hasSize(2);
        // And the caller is told what it is missing rather than inferring it from a short count.
        assertThat(result).containsEntry("unreadable", 1);
    }

    @Test
    @DisplayName("a listing with nothing wrong in it still says so")
    void aCleanListingReportsNoUnreadableRows() {
        assertThat(api.indexingLogging(0, 100, null, null, null, null, null, null, null, null,
                "desc")).containsEntry("unreadable", 0);
    }

    @Test
    @DisplayName("a row the appender actually wrote can be read back at all (D73)")
    void readsBackARowTheAppenderWrote() {
        // The seed goes through the same serializer the appender uses. While it emitted
        // `ISODate("…")` this returned an empty page for every query, because Jackson rejected
        // the first row and the failure was swallowed as "query failed".
        assertThat(api.indexingLogging(0, 100, null, null, null, null, null, null, null, null,
                "desc")).containsEntry("totalElements", 2L);
    }

    @Test
    @DisplayName("every filter the endpoint accepts narrows the result here too (D73)")
    void theFourFiltersThatUsedToBeDroppedAreHonoured() {
        // Each of these was accepted by the endpoint and never reached the Redis query, so each
        // used to answer 2 — the whole log, looking like a correct answer.
        assertThat(api.indexingLogging(0, 100, null, null, "INDEXED", null, null, null, null, null,
                "desc")).containsEntry("totalElements", 1L);
        assertThat(api.indexingLogging(0, 100, null, null, null, null, "ERROR", null, null, null,
                "desc")).containsEntry("totalElements", 1L);
        assertThat(api.indexingLogging(0, 100, null, null, null, null, null, "SITE/A", null, null,
                "desc")).as("url matches a case-insensitive substring, as it does on Mongo")
                .containsEntry("totalElements", 1L);
        assertThat(api.indexingLogging(0, 100, "2000-01-01", "2100-01-01", null, null, null, null,
                null, null, "desc")).containsEntry("totalElements", 2L);
        assertThat(api.indexingLogging(0, 100, "2100-01-01", null, null, null, null, null, null,
                null, "desc")).as("a range the rows fall outside excludes them")
                .containsEntry("totalElements", 0L);
    }
}
