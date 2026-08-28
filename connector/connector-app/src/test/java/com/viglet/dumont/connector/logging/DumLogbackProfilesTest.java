/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 */

package com.viglet.dumont.connector.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.status.Status;

/**
 * The logback profiles load only when someone sets {@code dumont.logging.engine}, so a broken
 * {@code appender-ref} in one of them is invisible until a deployment picks that engine — which
 * is exactly the deployment that then has no logging (roadmap <strong>D78</strong>).
 *
 * <p>
 * D78 added a {@code com.viglet.dumont.commons.logging} logger to the mongo and redis profiles,
 * pointing at {@code FILE}, an appender that only exists after the Spring Boot include further
 * down the file. Declared in the obvious place — beside the other loggers — it would have resolved
 * to nothing. Joran reports that as an ERROR status rather than an exception, so nothing fails
 * without something reading the statuses; this does.
 */
class DumLogbackProfilesTest {

    /**
     * The one error plain Joran is expected to raise here.
     *
     * <p>
     * {@code <springProperty>} is Spring Boot's extension, taught to Joran by a configurator that
     * is package-private, so this reads the files with the stock one and that element has no
     * action. Everything else Joran can resolve on its own — an {@code appender-ref} above all,
     * which is what this test is for. The filter is a literal element name rather than a pattern,
     * so a new kind of error cannot slip through it.
     */
    private static final String UNKNOWN_TO_PLAIN_JORAN = "springProperty";

    /**
     * What {@code <springProperty>} would have bound, seeded directly as context properties.
     *
     * <p>
     * Logback resolves {@code ${X}} against these, and the failed {@code springProperty} action
     * leaves them alone — so the appenders start against real values instead of the literal
     * {@code MONGO_URI_IS_UNDEFINED}. That is the difference between proving the file parses and
     * proving the appenders it declares can actually be constructed, which is the half that broke
     * when a URI or an entry count changed shape.
     */
    private static void seedSpringProperties(LoggerContext context) {
        context.putProperty("MONGO_URI", "mongodb://localhost:27017");
        context.putProperty("MONGO_ENABLED", "false");
        context.putProperty("LOG_DATABASE", "dumontLog");
        context.putProperty("LOG_COLL_SERVER", "server");
        context.putProperty("LOG_COLL_INDEXING", "indexing");
        context.putProperty("REDIS_URI", "redis://localhost:6379/0");
        context.putProperty("REDIS_ENABLED", "false");
        context.putProperty("REDIS_KEY_SERVER", "dumontLog:server");
        context.putProperty("REDIS_KEY_INDEXING", "dumontLog:indexing");
        context.putProperty("REDIS_MAX_SERVER", "10000");
        context.putProperty("REDIS_MAX_INDEXING", "50000");
    }

    @ParameterizedTest(name = "{0} configures without error")
    @ValueSource(strings = {"logback-spring.xml", "logback-spring-mongo.xml",
            "logback-spring-redis.xml", "logback-spring-console.xml"})
    @DisplayName("every logback profile resolves every appender it references (D78)")
    void profileConfiguresCleanly(String resourceName) throws JoranException {
        URL resource = getClass().getClassLoader().getResource(resourceName);
        assertThat(resource).as("%s is on the classpath", resourceName).isNotNull();

        // A private context, so configuring these files does not reconfigure the logging that the
        // rest of the suite is writing through.
        LoggerContext context = new LoggerContext();
        try {
            context.setName(resourceName);
            seedSpringProperties(context);
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(resource);

            List<String> errors = context.getStatusManager().getCopyOfStatusList().stream()
                    .filter(status -> status.getLevel() == Status.ERROR)
                    .map(Status::toString)
                    .filter(message -> !message.contains(UNKNOWN_TO_PLAIN_JORAN))
                    .toList();
            assertThat(errors).as("Joran ERROR statuses from %s", resourceName).isEmpty();
        } finally {
            // Releases the file appender the include created, so the run does not hold a handle
            // on a log file it never writes to.
            context.stop();
        }
    }
}
