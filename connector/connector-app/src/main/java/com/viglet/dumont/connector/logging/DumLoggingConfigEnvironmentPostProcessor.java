/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 */
package com.viglet.dumont.connector.logging;

import java.util.Locale;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Derives {@code logging.config} from {@code dumont.logging.engine}, so that turning the indexing
 * log on is one setting rather than two (roadmap <strong>D85</strong>).
 *
 * <p>
 * The two used to be unrelated. {@code dumont.logging.engine} configured the reader — the client
 * {@code DumLoggingAPI} pings — while the rows were written by a Logback appender chosen by
 * {@code logging.config}, named in a YAML comment and nowhere else. Every combination was accepted:
 * an engine without the profile wrote nothing, the profile without an engine wrote rows the API
 * refused to read, and a mongo profile against a redis engine did both at once. D84 made the first
 * of those visible on {@code logging/status}; this makes it unreachable.
 *
 * <p>
 * The derived value is contributed as the <em>last</em> property source, so anything an operator
 * sets — command line, environment, an external properties file — outranks it and a bespoke
 * Logback file still works. What is refused is a contradiction: an explicit {@code logging.config}
 * naming one of this project's own profiles while the engine names a different one. That is not a
 * configuration to honour, it is two instructions that disagree, and failing at startup with both
 * of them quoted beats writing to a store nobody is reading.
 *
 * <p>
 * Runs at {@link Ordered#LOWEST_PRECEDENCE} because it has to see the configuration files:
 * {@code ConfigDataEnvironmentPostProcessor} is what loads {@code application.yaml}, and it is an
 * environment post-processor too. Everything here still happens before the logging system starts.
 */
public class DumLoggingConfigEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    static final String ENGINE_PROPERTY = "dumont.logging.engine";
    static final String CONFIG_PROPERTY = "logging.config";
    static final String SOURCE_NAME = "dumontDerivedLoggingConfig";

    /** The engines this connector ships a Logback profile for. An engine absent here is left alone. */
    static final Map<String, String> PROFILE_BY_ENGINE = Map.of(
            "none", "classpath:logback-spring.xml",
            "mongodb", "classpath:logback-spring-mongo.xml",
            "redis", "classpath:logback-spring-redis.xml");

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
            SpringApplication application) {
        String engine = normalise(environment.getProperty(ENGINE_PROPERTY, "none"));
        String derived = PROFILE_BY_ENGINE.get(engine);
        if (derived == null) {
            // An engine this version does not know. Whatever is set stays set; complaining about
            // the logging profile would bury the real error, which is the engine name.
            return;
        }

        String explicit = environment.getProperty(CONFIG_PROPERTY);
        if (explicit != null && !explicit.isBlank()) {
            refuseAContradiction(engine, derived, explicit.trim());
            return;
        }

        environment.getPropertySources()
                .addLast(new MapPropertySource(SOURCE_NAME, Map.of(CONFIG_PROPERTY, derived)));
    }

    /**
     * A bespoke Logback file is none of this class's business; one of ours naming another engine
     * is. Only the shipped profiles are recognised, so an operator pointing at their own file —
     * whatever it contains — passes through untouched.
     */
    private void refuseAContradiction(String engine, String derived, String explicit) {
        boolean isOneOfOurs = PROFILE_BY_ENGINE.containsValue(explicit);
        if (!isOneOfOurs || explicit.equals(derived)) {
            return;
        }
        throw new IllegalStateException(
                "Contradictory logging configuration: %s is '%s', which writes the log through '%s', but %s is set to '%s'. Set them to the same engine, or leave %s unset and it will be derived."
                        .formatted(ENGINE_PROPERTY, engine, derived, CONFIG_PROPERTY, explicit,
                                CONFIG_PROPERTY));
    }

    private static String normalise(String engine) {
        return engine == null ? "none" : engine.trim().toLowerCase(Locale.ROOT);
    }
}
