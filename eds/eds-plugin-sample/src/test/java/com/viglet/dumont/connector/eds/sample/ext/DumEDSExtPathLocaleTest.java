/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.eds.sample.ext;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.eds.commons.DumEDSContext;
import com.viglet.dumont.connector.eds.commons.ext.DumEDSExtLocaleInterface;

class DumEDSExtPathLocaleTest {

    private final DumEDSExtPathLocale ext = new DumEDSExtPathLocale();

    @Test
    void ptBrPathResolvesToPtBR() {
        Locale result = ext.consume(DumEDSContext.builder()
                .path("/pt-br/pf/cartao-de-credito").build());

        assertThat(result).isEqualTo(Locale.forLanguageTag("pt-BR"));
    }

    @Test
    void enUsPathResolvesToEnUS() {
        Locale result = ext.consume(DumEDSContext.builder()
                .path("/en-us/sala-de-imprensa").build());

        assertThat(result).isEqualTo(Locale.forLanguageTag("en-US"));
    }

    @Test
    void languageOnlySegmentResolvesToLanguageOnlyLocale() {
        Locale result = ext.consume(DumEDSContext.builder()
                .path("/de/produkte").build());

        assertThat(result.getLanguage()).isEqualTo("de");
        assertThat(result.getCountry()).isEmpty();
    }

    @Test
    void rootPathFallsBackToUS() {
        assertThat(ext.consume(DumEDSContext.builder().path("/").build())).isEqualTo(Locale.US);
        assertThat(ext.consume(DumEDSContext.builder().path("").build())).isEqualTo(Locale.US);
    }

    @Test
    void nullContextOrPathFallsBackToUS() {
        assertThat(ext.consume(null)).isEqualTo(Locale.US);
        assertThat(ext.consume(DumEDSContext.builder().path(null).build())).isEqualTo(Locale.US);
    }

    @Test
    void nonLocaleSegmentFallsBackToUS() {
        // /footer, /content, /spreadsheets — site-level non-locale prefixes
        Locale result = ext.consume(DumEDSContext.builder().path("/footer").build());

        assertThat(result).isEqualTo(Locale.US);
    }

    @Test
    void canonicalisesMixedCaseTags() {
        Locale result = ext.consume(DumEDSContext.builder().path("/PT-br/pf").build());

        assertThat(result).isEqualTo(Locale.forLanguageTag("pt-BR"));
    }

    @Test
    void implementsTheExtensionInterface() {
        assertThat(ext).isInstanceOf(DumEDSExtLocaleInterface.class);
    }
}
