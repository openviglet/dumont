/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.viglet.dumont.connector.plugin.aem;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * Test-only configuration anchor for Spring Boot slice tests in this library module.
 * <p>
 * {@code aem-plugin} ships no {@code @SpringBootApplication} of its own (the runnable app lives in
 * {@code connector-app}), so slice tests such as {@code @DataJpaTest} could not find a
 * {@code @SpringBootConfiguration} by searching upward from the test package and failed to bootstrap.
 * This minimal anchor sits above {@code …plugin.aem.api} so those tests resolve it; the slice
 * annotation still restricts the loaded context to the relevant auto-configurations.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class DumAemPluginTestApplication {
}
