/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.viglet.dumont.connector.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class DumLoggingAPITest {

    private DumLoggingAPI api(String engine, boolean mongoEnabled, boolean redisEnabled) {
        return new DumLoggingAPI(engine, mongoEnabled, "mongodb://localhost:27017", "dumontLog",
                "indexing", redisEnabled, "redis://localhost:6379/0", "dumontLog:indexing");
    }

    @Test
    void statusReportsEngineAndDisabledWhenNone() {
        Map<String, Object> status = api("none", false, false).status();
        assertThat(status).containsEntry("engine", "none").containsEntry("enabled", false);
    }

    @Test
    void statusEnabledFalseWhenEngineSetButBackendDisabled() {
        // engine=mongodb but mongoEnabled=false -> not active
        assertThat(api("mongodb", false, false).status()).containsEntry("enabled", false);
        // engine=redis but redisEnabled=false -> not active
        assertThat(api("redis", false, false).status()).containsEntry("enabled", false);
    }

    @Test
    void indexingLoggingReturnsEmptyPageWhenNoActiveEngine() {
        Map<String, Object> result = api("none", false, false)
                .indexingLogging(0, 100, null, null, null, null, null, null, "desc");

        assertThat(result).containsEntry("page", 0).containsEntry("pageSize", 100)
                .containsEntry("totalElements", 0L).containsEntry("totalPages", 0);
        assertThat((Iterable<?>) result.get("content")).isEmpty();
    }
}
