/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.viglet.dumont.connector.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import redis.clients.jedis.Jedis;

/**
 * Integration test for {@link DumLoggingAPI}'s Redis engine, backed by a real Redis via
 * Testcontainers. Exercises the list read, contentId filter, sort and pagination paths.
 */
@Testcontainers(disabledWithoutDocker = true)
class DumLoggingApiRedisIT {

    private static final String KEY = "dumontLog:indexing";

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
            jedis.lpush(KEY, "{\"contentId\":\"c1\",\"status\":\"INDEXED\"}");
            jedis.lpush(KEY, "{\"contentId\":\"c2\",\"status\":\"DEINDEXED\"}");
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
                "desc");
        assertThat(result).containsEntry("totalElements", 2L);
        assertThat((List<?>) result.get("content")).hasSize(2);
    }

    @Test
    void filtersByContentIdAndSortsAscending() {
        Map<String, Object> result = api.indexingLogging(0, 100, null, null, null, "c1", null, null,
                "asc");
        assertThat(result).containsEntry("totalElements", 1L);
    }

    @Test
    void paginatesResults() {
        Map<String, Object> page0 = api.indexingLogging(0, 1, null, null, null, null, null, null,
                "desc");
        assertThat((List<?>) page0.get("content")).hasSize(1);
        assertThat(page0).containsEntry("totalElements", 2L);
    }
}
