/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 */

package com.viglet.dumont.connector.logging;

import static com.viglet.dumont.connector.logging.DumLoggingConfigEnvironmentPostProcessor.CONFIG_PROPERTY;
import static com.viglet.dumont.connector.logging.DumLoggingConfigEnvironmentPostProcessor.ENGINE_PROPERTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.mock.env.MockEnvironment;

/**
 * D85 — the indexing log used to need two settings that nothing related, so every mismatched
 * combination was accepted and D84 could only report the result after the fact. These are the
 * combinations, and what each one should now do.
 */
class DumLoggingConfigEnvironmentPostProcessorTest {

    private final DumLoggingConfigEnvironmentPostProcessor processor =
            new DumLoggingConfigEnvironmentPostProcessor();

    private MockEnvironment environmentWith(String engine, String explicitConfig) {
        MockEnvironment environment = new MockEnvironment();
        if (engine != null) {
            environment.setProperty(ENGINE_PROPERTY, engine);
        }
        if (explicitConfig != null) {
            environment.setProperty(CONFIG_PROPERTY, explicitConfig);
        }
        return environment;
    }

    @ParameterizedTest(name = "engine {0} selects {1}")
    @CsvSource({
            "none,     classpath:logback-spring.xml",
            "mongodb,  classpath:logback-spring-mongo.xml",
            "redis,    classpath:logback-spring-redis.xml",
            "MongoDB,  classpath:logback-spring-mongo.xml",
            "  redis , classpath:logback-spring-redis.xml",
    })
    @DisplayName("the engine alone selects the profile that writes the log (D85)")
    void derivesTheProfileFromTheEngine(String engine, String expected) {
        MockEnvironment environment = environmentWith(engine, null);

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty(CONFIG_PROPERTY)).isEqualTo(expected);
    }

    @Test
    @DisplayName("an unset engine is the same as none")
    void defaultsToNone() {
        MockEnvironment environment = environmentWith(null, null);

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty(CONFIG_PROPERTY))
                .isEqualTo("classpath:logback-spring.xml");
    }

    @Test
    @DisplayName("a Logback file of the operator's own is left alone")
    void anExplicitCustomFileWins() {
        // The derivation is contributed as the lowest-precedence source precisely so this keeps
        // working; a bespoke file is none of this class's business whatever it contains.
        MockEnvironment environment = environmentWith("mongodb", "file:/etc/dumont/logback.xml");

        assertThatCode(() -> processor.postProcessEnvironment(environment, null))
                .doesNotThrowAnyException();

        assertThat(environment.getProperty(CONFIG_PROPERTY))
                .isEqualTo("file:/etc/dumont/logback.xml");
    }

    @Test
    @DisplayName("the console profile is still allowed when no engine is named")
    void theConsoleProfileWithoutAnEngineIsFine() {
        // It writes no store and no store was asked for, so there is nothing to disagree about.
        // Redundant since D86 — every profile writes to the console now — but not wrong.
        MockEnvironment environment =
                environmentWith("none", "classpath:logback-spring-console.xml");

        assertThatCode(() -> processor.postProcessEnvironment(environment, null))
                .doesNotThrowAnyException();

        assertThat(environment.getProperty(CONFIG_PROPERTY))
                .isEqualTo("classpath:logback-spring-console.xml");
    }

    @Test
    @DisplayName("setting the matching profile by hand is redundant, not wrong")
    void anExplicitMatchingProfileIsAccepted() {
        MockEnvironment environment =
                environmentWith("redis", "classpath:logback-spring-redis.xml");

        assertThatCode(() -> processor.postProcessEnvironment(environment, null))
                .doesNotThrowAnyException();

        assertThat(environment.getProperty(CONFIG_PROPERTY))
                .isEqualTo("classpath:logback-spring-redis.xml");
    }

    @ParameterizedTest(name = "engine {0} against profile {1} is refused")
    @CsvSource({
            "mongodb, classpath:logback-spring-redis.xml",
            "redis,   classpath:logback-spring-mongo.xml",
            "mongodb, classpath:logback-spring.xml",
            "none,    classpath:logback-spring-mongo.xml",
            // The console profile writes no store, so naming an engine beside it is the same
            // contradiction. This is what the Docker images pinned before D86.
            "mongodb, classpath:logback-spring-console.xml",
            "redis,   classpath:logback-spring-console.xml",
    })
    @DisplayName("two shipped profiles that disagree are refused at startup, not half-honoured")
    void refusesAContradiction(String engine, String explicitConfig) {
        /*
         * `mongodb` against the plain profile is the exact state D84 was built to report: the
         * reader reaches the store, nothing writes to it, and the history is empty forever. Now
         * the only way to reach it is to ask for it explicitly, and asking is refused.
         */
        MockEnvironment environment = environmentWith(engine, explicitConfig);

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ENGINE_PROPERTY)
                .hasMessageContaining(CONFIG_PROPERTY)
                .hasMessageContaining(explicitConfig);
    }

    @Test
    @DisplayName("spring.factories registers it under the interface's real name (D85)")
    void isRegisteredWhereSpringLooks() throws IOException {
        /*
         * Everything above tests a class that nothing would ever call if this were wrong, and
         * wrong is easy: `EnvironmentPostProcessor` moved from org.springframework.boot.env to
         * org.springframework.boot in Spring Boot 4, and registering under the old name fails
         * silently — no error, no post-processor, `logging.config` never derived. Asserting
         * against the interface's own getName() means the key cannot drift from the type again.
         */
        Enumeration<URL> resources =
                getClass().getClassLoader().getResources("META-INF/spring.factories");
        List<String> registered = new ArrayList<>();
        while (resources.hasMoreElements()) {
            Properties factories = new Properties();
            try (InputStream stream = resources.nextElement().openStream()) {
                factories.load(stream);
            }
            // Every jar on the classpath ships one of these, so collect rather than take the first.
            String value = factories.getProperty(EnvironmentPostProcessor.class.getName());
            if (value != null) {
                registered.add(value);
            }
        }

        assertThat(registered)
                .as("%s registrations found on the classpath", EnvironmentPostProcessor.class)
                .isNotEmpty()
                .anySatisfy(value -> assertThat(value)
                        .contains(DumLoggingConfigEnvironmentPostProcessor.class.getName()));
    }

    @Test
    @DisplayName("an engine this version does not know is left for the engine's own validation")
    void anUnknownEngineIsNotThisClassesProblem() {
        // Deriving nothing beats guessing, and complaining about the profile would bury the real
        // error, which is the engine name.
        MockEnvironment environment = environmentWith("cassandra", null);

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty(CONFIG_PROPERTY)).isNull();
    }
}
