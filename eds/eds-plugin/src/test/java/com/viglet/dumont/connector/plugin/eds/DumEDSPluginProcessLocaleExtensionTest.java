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

package com.viglet.dumont.connector.plugin.eds;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.commons.cache.DumCustomClassCache;
import com.viglet.dumont.connector.eds.commons.DumEDSContext;
import com.viglet.dumont.connector.eds.commons.domain.DumEDSDiscoveredPage;
import com.viglet.dumont.connector.eds.commons.ext.DumEDSExtLocaleInterface;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;

/**
 * Verifies the wiring point between {@link DumEDSPluginProcess#resolveLocaleFromExtension}
 * and the {@link DumCustomClassCache} external-resolver hook. Plugin authors
 * register their classes via the cache; the plugin process then instantiates
 * them and routes locale resolution per page.
 *
 * <p>The actual sample implementations ({@code DumEDSExtPathLocale}) live in
 * the sibling {@code eds-plugin-sample} module — this test stays here so the
 * dependency direction (plugin ← samples) stays one-way.
 */
class DumEDSPluginProcessLocaleExtensionTest {

    private static final String EXTENSION_FQN = "com.example.test.MyLocaleExt";

    @AfterEach
    void clearCache() {
        DumCustomClassCache.setExternalResolver(null);
        DumCustomClassCache.clear();
    }

    @Test
    void resolverInvokedWhenLocaleClassConfiguredAndSourceLocaleNull() {
        Locale picked = Locale.forLanguageTag("pt-BR");
        DumEDSExtLocaleInterface ext = context -> {
            // Sanity: the context carries all the per-page info we expect.
            assertThat(context.getPath()).isEqualTo("/pt-br/pf");
            assertThat(context.getUrl()).isEqualTo("https://example.invalid/pt-br/pf");
            assertThat(context.getDocument()).isNotNull();
            return picked;
        };
        DumCustomClassCache.setExternalResolver(fqn ->
                EXTENSION_FQN.equals(fqn) ? Optional.of(ext) : Optional.empty());

        DumEDSSource source = source(null, EXTENSION_FQN);
        DumEDSDiscoveredPage page = DumEDSDiscoveredPage.fromIndex("/pt-br/pf", Map.of());
        Document doc = Jsoup.parse("<html><head><title>x</title></head></html>");

        Locale result = DumEDSPluginProcess.resolveLocaleFromExtension(
                source, page, URI.create("https://example.invalid/pt-br/pf"), doc);

        assertThat(result).isEqualTo(picked);
    }

    @Test
    void sourceLocaleShortCircuitsExtensionLookup() {
        // Even with localeClass configured, an explicit source.locale wins —
        // the resolver returns null so the builder falls back to source.locale.
        DumCustomClassCache.setExternalResolver(fqn -> {
            throw new AssertionError("Extension lookup must not happen when source.locale is set");
        });

        DumEDSSource source = source(Locale.forLanguageTag("pt-BR"), EXTENSION_FQN);
        DumEDSDiscoveredPage page = DumEDSDiscoveredPage.fromIndex("/pt-br/pf", Map.of());

        Locale result = DumEDSPluginProcess.resolveLocaleFromExtension(source, page,
                URI.create("https://example.invalid/pt-br/pf"), null);

        assertThat(result).isNull();
    }

    @Test
    void missingLocaleClassReturnsNullWithoutCacheInteraction() {
        DumCustomClassCache.setExternalResolver(fqn -> {
            throw new AssertionError("Should not be called when localeClass is blank");
        });

        DumEDSSource source = source(null, null);
        DumEDSDiscoveredPage page = DumEDSDiscoveredPage.fromIndex("/anything", Map.of());

        Locale result = DumEDSPluginProcess.resolveLocaleFromExtension(source, page,
                URI.create("https://example.invalid/anything"), null);

        assertThat(result).isNull();
    }

    @Test
    void extensionExceptionDowngradesToNullLocale() {
        DumEDSExtLocaleInterface failing = context -> {
            throw new RuntimeException("simulated failure");
        };
        DumCustomClassCache.setExternalResolver(fqn -> Optional.of(failing));

        DumEDSSource source = source(null, EXTENSION_FQN);
        DumEDSDiscoveredPage page = DumEDSDiscoveredPage.fromIndex("/x", Map.of());

        Locale result = DumEDSPluginProcess.resolveLocaleFromExtension(source, page,
                URI.create("https://example.invalid/x"), null);

        // Failure surfaces as a warn log + null locale; builder will fall back to Locale.US.
        assertThat(result).isNull();
    }

    @Test
    void contextIsBuiltWithExpectedShape() {
        // Captures the DumEDSContext to assert all builder fields propagate.
        DumEDSContext[] captured = new DumEDSContext[1];
        DumEDSExtLocaleInterface ext = context -> {
            captured[0] = context;
            return Locale.US;
        };
        DumCustomClassCache.setExternalResolver(fqn -> Optional.of(ext));

        DumEDSSource source = source(null, EXTENSION_FQN);
        source.setUserAgent("UnitTest-UA/1.0");
        DumEDSDiscoveredPage page = DumEDSDiscoveredPage.fromIndex(
                "/pt-br/pf", Map.of("path", "/pt-br/pf", "title", "PF"));

        DumEDSPluginProcess.resolveLocaleFromExtension(source, page,
                URI.create("https://example.invalid/pt-br/pf"), null);

        assertThat(captured[0]).isNotNull();
        assertThat(captured[0].getPath()).isEqualTo("/pt-br/pf");
        assertThat(captured[0].getBaseUrl()).isEqualTo("https://example.invalid");
        assertThat(captured[0].getUserAgent()).isEqualTo("UnitTest-UA/1.0");
        assertThat(captured[0].getJsonMetadata()).containsEntry("title", "PF");
    }

    private static DumEDSSource source(Locale explicitLocale, String localeClass) {
        DumEDSSource s = new DumEDSSource();
        s.setId("test-id");
        s.setTitle("test");
        s.setBaseUrl("https://example.invalid");
        s.setLocale(explicitLocale);
        s.setLocaleClass(localeClass);
        return s;
    }
}
