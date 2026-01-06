/*
 * Copyright (C) 2016-2025 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.aem.export.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Collection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DumAemExchange Tests")
class DumAemExchangeTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create instance with builder")
        void shouldCreateInstanceWithBuilder() {
            DumAemSourceExchange source1 = DumAemSourceExchange.builder().id("source1").build();
            DumAemSourceExchange source2 = DumAemSourceExchange.builder().id("source2").build();

            DumAemExchange exchange = DumAemExchange.builder()
                    .sources(Arrays.asList(source1, source2))
                    .build();

            assertNotNull(exchange.getSources());
            assertEquals(2, exchange.getSources().size());
        }

        @Test
        @DisplayName("Should create instance with empty sources")
        void shouldCreateInstanceWithEmptySources() {
            DumAemExchange exchange = DumAemExchange.builder().build();

            assertNull(exchange.getSources());
        }

        @Test
        @DisplayName("Should support toBuilder")
        void shouldSupportToBuilder() {
            DumAemExchange original = DumAemExchange.builder()
                    .sources(Arrays.asList(DumAemSourceExchange.builder().id("source1").build()))
                    .build();

            DumAemExchange modified = original.toBuilder()
                    .sources(Arrays.asList(DumAemSourceExchange.builder().id("source2").build()))
                    .build();

            assertNotEquals(original.getSources(), modified.getSources());
        }
    }

    @Nested
    @DisplayName("No Args Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create instance with no args constructor")
        void shouldCreateInstanceWithNoArgsConstructor() {
            DumAemExchange exchange = new DumAemExchange();

            assertNotNull(exchange);
            assertNull(exchange.getSources());
        }
    }

    @Nested
    @DisplayName("All Args Constructor Tests")
    class AllArgsConstructorTests {

        @Test
        @DisplayName("Should create instance with all args constructor")
        void shouldCreateInstanceWithAllArgsConstructor() {
            Collection<DumAemSourceExchange> sources = Arrays.asList(
                    DumAemSourceExchange.builder().id("s1").build(),
                    DumAemSourceExchange.builder().id("s2").build());

            DumAemExchange exchange = new DumAemExchange(sources);

            assertNotNull(exchange.getSources());
            assertEquals(2, exchange.getSources().size());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get sources")
        void shouldSetAndGetSources() {
            DumAemExchange exchange = new DumAemExchange();
            Collection<DumAemSourceExchange> sources = Arrays.asList(
                    DumAemSourceExchange.builder().id("source1").build());
            exchange.setSources(sources);

            assertNotNull(exchange.getSources());
            assertEquals(1, exchange.getSources().size());
        }
    }
}
