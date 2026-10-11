/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.aem;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.util.ClassUtils;

import com.viglet.dumont.connector.plugin.aem.conf.DumAemJournalProperties;
import com.viglet.dumont.connector.plugin.aem.service.DumAemImsTokenService;

/**
 * Every component the AEM plugin contributes must have a constructor Spring can choose (roadmap
 * <strong>D123</strong>).
 *
 * <p>
 * A bean with two constructors and neither marked {@code @Autowired} makes Spring fall back to a
 * no-argument one; without it the context fails, and the connector does not start with
 * {@code aem-plugin.jar} loaded. Unit tests that call a constructor directly cannot see this.
 */
@DisplayName("AEM plugin bean constructors (D123)")
class DumAemBeanConstructorTest {

    private static final String BASE_PACKAGE = "com.viglet.dumont.connector.plugin.aem";

    @Test
    @DisplayName("every component has a constructor Spring can pick")
    void everyComponentHasAnUnambiguousConstructor() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(true);
        List<String> ambiguous = new ArrayList<>();
        int scanned = 0;
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> type = ClassUtils.forName(definition.getBeanClassName(), getClass().getClassLoader());
            scanned++;
            if (!hasPickableConstructor(type)) {
                ambiguous.add(type.getName());
            }
        }
        assertThat(scanned).as("components scanned under %s", BASE_PACKAGE).isPositive();
        assertThat(ambiguous).as("components with several constructors, none @Autowired and no default")
                .isEmpty();
    }

    @Test
    @DisplayName("the IMS token service is created by an application context")
    void imsTokenServiceBootsInAContext() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                DumAemJournalProperties.class, DumAemImsTokenService.class)) {
            assertThat(context.getBean(DumAemImsTokenService.class)).isNotNull();
        }
    }

    private static boolean hasPickableConstructor(Class<?> type) {
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        if (constructors.length <= 1) {
            return true;
        }
        return Arrays.stream(constructors).anyMatch(c -> c.isAnnotationPresent(Autowired.class))
                || Arrays.stream(constructors).anyMatch(c -> c.getParameterCount() == 0);
    }
}
