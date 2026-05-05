/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 */
package com.viglet.dumont.commons.logging;

import java.net.URI;
import java.time.Duration;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.util.JedisURIHelper;

/**
 * Base class for Redis-backed Logback appenders. Mirrors
 * {@code TurRedisAppenderBase}: manages a {@link RedisClient} life cycle
 * (built on a pooled connection provider) and exposes the configuration knobs
 * ({@code connectionString}, {@code key}, {@code maxEntries}). Subclasses are
 * responsible for the actual {@code LPUSH}.
 */
@Slf4j
@Setter
public class DumRedisAppenderBase extends AppenderBase<ILoggingEvent> {
    protected boolean enabled;
    protected String connectionString;
    protected String key;
    protected int maxEntries;
    protected RedisClient pool;

    @Override
    public void start() {
        try {
            URI uri = URI.create(connectionString);
            ConnectionPoolConfig cfg = new ConnectionPoolConfig();
            cfg.setMaxTotal(50);
            cfg.setMaxWait(Duration.ofSeconds(2));
            this.pool = RedisClient.builder()
                    .hostAndPort(JedisURIHelper.getHostAndPort(uri))
                    .clientConfig(DefaultJedisClientConfig.builder(uri).build())
                    .poolConfig(cfg)
                    .build();
            super.start();
        } catch (Exception e) {
            addError("Failed to initialize Redis Appender", e);
        }
    }

    @Override
    public void stop() {
        if (pool != null) {
            pool.close();
        }
        super.stop();
    }

    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        // Implementation in subclass
    }
}
