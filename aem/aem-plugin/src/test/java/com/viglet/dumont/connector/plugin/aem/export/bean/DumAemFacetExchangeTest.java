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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DumAemFacetExchange Tests")
class DumAemFacetExchangeTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create instance with builder")
        void shouldCreateInstanceWithBuilder() {
            DumAemFacetExchange exchange = DumAemFacetExchange.builder()
                    .ptBR("Nome PT-BR")
                    .enUS("Name EN-US")
                    .pt("Nome PT")
                    .en("Name EN")
                    .defaultName("Default Name")
                    .build();

            assertEquals("Nome PT-BR", exchange.getPtBR());
            assertEquals("Name EN-US", exchange.getEnUS());
            assertEquals("Nome PT", exchange.getPt());
            assertEquals("Name EN", exchange.getEn());
            assertEquals("Default Name", exchange.getDefaultName());
        }

        @Test
        @DisplayName("Should support toBuilder")
        void shouldSupportToBuilder() {
            DumAemFacetExchange original = DumAemFacetExchange.builder()
                    .ptBR("Original")
                    .build();

            DumAemFacetExchange modified = original.toBuilder()
                    .ptBR("Modified")
                    .enUS("Added")
                    .build();

            assertEquals("Modified", modified.getPtBR());
            assertEquals("Added", modified.getEnUS());
        }
    }

    @Nested
    @DisplayName("No Args Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create instance with no args constructor")
        void shouldCreateInstanceWithNoArgsConstructor() {
            DumAemFacetExchange exchange = new DumAemFacetExchange();

            assertNotNull(exchange);
            assertNull(exchange.getPtBR());
            assertNull(exchange.getEnUS());
            assertNull(exchange.getPt());
            assertNull(exchange.getEn());
            assertNull(exchange.getDefaultName());
        }
    }

    @Nested
    @DisplayName("All Args Constructor Tests")
    class AllArgsConstructorTests {

        @Test
        @DisplayName("Should create instance with all args constructor")
        void shouldCreateInstanceWithAllArgsConstructor() {
            DumAemFacetExchange exchange = new DumAemFacetExchange(
                    "PT-BR", "EN-US", "PT", "EN", "Default");

            assertEquals("PT-BR", exchange.getPtBR());
            assertEquals("EN-US", exchange.getEnUS());
            assertEquals("PT", exchange.getPt());
            assertEquals("EN", exchange.getEn());
            assertEquals("Default", exchange.getDefaultName());
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get ptBR")
        void shouldSetAndGetPtBR() {
            DumAemFacetExchange exchange = new DumAemFacetExchange();
            exchange.setPtBR("Categoria");

            assertEquals("Categoria", exchange.getPtBR());
        }

        @Test
        @DisplayName("Should set and get enUS")
        void shouldSetAndGetEnUS() {
            DumAemFacetExchange exchange = new DumAemFacetExchange();
            exchange.setEnUS("Category");

            assertEquals("Category", exchange.getEnUS());
        }

        @Test
        @DisplayName("Should set and get pt")
        void shouldSetAndGetPt() {
            DumAemFacetExchange exchange = new DumAemFacetExchange();
            exchange.setPt("Categoria PT");

            assertEquals("Categoria PT", exchange.getPt());
        }

        @Test
        @DisplayName("Should set and get en")
        void shouldSetAndGetEn() {
            DumAemFacetExchange exchange = new DumAemFacetExchange();
            exchange.setEn("Category EN");

            assertEquals("Category EN", exchange.getEn());
        }

        @Test
        @DisplayName("Should set and get defaultName")
        void shouldSetAndGetDefaultName() {
            DumAemFacetExchange exchange = new DumAemFacetExchange();
            exchange.setDefaultName("Default Category");

            assertEquals("Default Category", exchange.getDefaultName());
        }
    }
}
