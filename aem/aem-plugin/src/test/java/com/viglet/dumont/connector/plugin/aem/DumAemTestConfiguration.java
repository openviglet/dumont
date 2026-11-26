package com.viglet.dumont.connector.plugin.aem;

import org.springframework.boot.SpringBootConfiguration;

/**
 * The configuration a Spring test slice finds by searching packages upwards (roadmap DU13).
 *
 * <p>The plugin ships no {@code @SpringBootApplication} of its own (connector-app does), so a
 * {@code @DataJpaTest} here stopped at "Unable to find a @SpringBootConfiguration" before running a
 * single case. Deliberately bare: the slice brings its own auto-configuration, and each test names
 * what it scans and imports, so nothing here widens what a test loads.
 */
@SpringBootConfiguration
public class DumAemTestConfiguration {
}
