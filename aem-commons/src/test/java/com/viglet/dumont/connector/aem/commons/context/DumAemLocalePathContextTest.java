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

package com.viglet.dumont.connector.aem.commons.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DumAemLocalePathContext Tests")
class DumAemLocalePathContextTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build locale path context with all fields")
        void shouldBuildLocalePathContextWithAllFields() {
            DumAemLocalePathContext localePath = DumAemLocalePathContext.builder()
                    .path("/content/mysite/pt-br")
                    .locale(Locale.of("pt", "BR"))
                    .build();

            assertEquals("/content/mysite/pt-br", localePath.getPath());
            assertEquals(Locale.of("pt", "BR"), localePath.getLocale());
        }

        @Test
        @DisplayName("Should build locale path context with English locale")
        void shouldBuildLocalePathContextWithEnglishLocale() {
            DumAemLocalePathContext localePath = DumAemLocalePathContext.builder()
                    .path("/content/mysite/en")
                    .locale(Locale.ENGLISH)
                    .build();

            assertEquals("/content/mysite/en", localePath.getPath());
            assertEquals(Locale.ENGLISH, localePath.getLocale());
        }

        @Test
        @DisplayName("Should build locale path context with French locale")
        void shouldBuildLocalePathContextWithFrenchLocale() {
            DumAemLocalePathContext localePath = DumAemLocalePathContext.builder()
                    .path("/content/mysite/fr")
                    .locale(Locale.FRENCH)
                    .build();

            assertEquals("/content/mysite/fr", localePath.getPath());
            assertEquals(Locale.FRENCH, localePath.getLocale());
        }
    }

    @Nested
    @DisplayName("Getter Tests")
    class GetterTests {

        @Test
        @DisplayName("Should get path")
        void shouldGetPath() {
            DumAemLocalePathContext localePath = DumAemLocalePathContext.builder()
                    .path("/content/site/es")
                    .locale(Locale.of("es", "ES"))
                    .build();

            assertEquals("/content/site/es", localePath.getPath());
        }

        @Test
        @DisplayName("Should get locale")
        void shouldGetLocale() {
            Locale spanishLocale = Locale.of("es", "ES");
            DumAemLocalePathContext localePath = DumAemLocalePathContext.builder()
                    .path("/content/site/es")
                    .locale(spanishLocale)
                    .build();

            assertEquals(spanishLocale, localePath.getLocale());
        }
    }

    @Nested
    @DisplayName("NoArgs Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create locale path context with default values")
        void shouldCreateLocalePathContextWithDefaultValues() {
            DumAemLocalePathContext localePath = new DumAemLocalePathContext();

            assertNull(localePath.getPath());
            assertNull(localePath.getLocale());
        }
    }

    @Nested
    @DisplayName("AllArgs Constructor Tests")
    class AllArgsConstructorTests {

        @Test
        @DisplayName("Should create locale path context with all args")
        void shouldCreateLocalePathContextWithAllArgs() {
            DumAemLocalePathContext localePath = new DumAemLocalePathContext(
                    "authorSite",
                    Locale.GERMAN,
                    "/content/site/de");

            assertEquals("/content/site/de", localePath.getPath());
            assertEquals(Locale.GERMAN, localePath.getLocale());
            assertEquals("authorSite", localePath.getSnSite());
        }
    }

    @Nested
    @DisplayName("Setter Tests")
    class SetterTests {

        @Test
        @DisplayName("Should set path")
        void shouldSetPath() {
            DumAemLocalePathContext localePath = new DumAemLocalePathContext();
            localePath.setPath("/content/new/path");

            assertEquals("/content/new/path", localePath.getPath());
        }

        @Test
        @DisplayName("Should set locale")
        void shouldSetLocale() {
            DumAemLocalePathContext localePath = new DumAemLocalePathContext();
            localePath.setLocale(Locale.ITALIAN);

            assertEquals(Locale.ITALIAN, localePath.getLocale());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle null path")
        void shouldHandleNullPath() {
            DumAemLocalePathContext localePath = DumAemLocalePathContext.builder()
                    .path(null)
                    .locale(Locale.ENGLISH)
                    .build();

            assertNull(localePath.getPath());
        }

        @Test
        @DisplayName("Should handle null locale")
        void shouldHandleNullLocale() {
            DumAemLocalePathContext localePath = DumAemLocalePathContext.builder()
                    .path("/content/site")
                    .locale(null)
                    .build();

            assertNull(localePath.getLocale());
        }

        @Test
        @DisplayName("Should handle empty path")
        void shouldHandleEmptyPath() {
            DumAemLocalePathContext localePath = DumAemLocalePathContext.builder()
                    .path("")
                    .locale(Locale.ENGLISH)
                    .build();

            assertEquals("", localePath.getPath());
        }
    }
}
