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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DumAemSourceLocalePathExchange Tests")
class DumAemSourceLocalePathExchangeTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create instance with builder")
        void shouldCreateInstanceWithBuilder() {
            DumAemSourceLocalePathExchange exchange = DumAemSourceLocalePathExchange.builder()
                    .locale(Locale.US)
                    .path("/content/mysite/en")
                    .build();

            assertEquals(Locale.US, exchange.getLocale());
            assertEquals("/content/mysite/en", exchange.getPath());
        }

        @Test
        @DisplayName("Should support toBuilder")
        void shouldSupportToBuilder() {
            DumAemSourceLocalePathExchange original = DumAemSourceLocalePathExchange.builder()
                    .locale(Locale.US)
                    .path("/content/en")
                    .build();

            DumAemSourceLocalePathExchange modified = original.toBuilder()
                    .locale(Locale.GERMANY)
                    .path("/content/de")
                    .build();

            assertEquals(Locale.GERMANY, modified.getLocale());
            assertEquals("/content/de", modified.getPath());
        }
    }

    @Nested
    @DisplayName("No Args Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create instance with no args constructor")
        void shouldCreateInstanceWithNoArgsConstructor() {
            DumAemSourceLocalePathExchange exchange = new DumAemSourceLocalePathExchange();

            assertNotNull(exchange);
            assertNull(exchange.getLocale());
            assertNull(exchange.getPath());
        }
    }

    @Nested
    @DisplayName("All Args Constructor Tests")
    class AllArgsConstructorTests {

        @Test
        @DisplayName("Should create instance with all args constructor")
        void shouldCreateInstanceWithAllArgsConstructor() {
            DumAemSourceLocalePathExchange exchange = new DumAemSourceLocalePathExchange(
                    Locale.FRANCE, "/content/fr");

            assertEquals(Locale.FRANCE, exchange.getLocale());
            assertEquals("/content/fr", exchange.getPath());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get locale")
        void shouldSetAndGetLocale() {
            DumAemSourceLocalePathExchange exchange = new DumAemSourceLocalePathExchange();
            exchange.setLocale(Locale.JAPAN);

            assertEquals(Locale.JAPAN, exchange.getLocale());
        }

        @Test
        @DisplayName("Should set and get path")
        void shouldSetAndGetPath() {
            DumAemSourceLocalePathExchange exchange = new DumAemSourceLocalePathExchange();
            exchange.setPath("/content/jp");

            assertEquals("/content/jp", exchange.getPath());
        }

        @Test
        @DisplayName("Should handle Brazilian Portuguese locale")
        void shouldHandleBrazilianPortugueseLocale() {
            DumAemSourceLocalePathExchange exchange = new DumAemSourceLocalePathExchange();
            Locale ptBR = Locale.of("pt", "BR");
            exchange.setLocale(ptBR);
            exchange.setPath("/content/pt-br");

            assertEquals("pt", exchange.getLocale().getLanguage());
            assertEquals("BR", exchange.getLocale().getCountry());
            assertEquals("/content/pt-br", exchange.getPath());
        }
    }
}
