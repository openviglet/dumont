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
 */

package com.viglet.dumont.connector.commons.spi.structured.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourcePlugin;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.field.TurSNFieldName;

/**
 * End-to-end test for roadmap <strong>D02</strong>: a structured source built on
 * {@link DumTabularListingStrategy} (no bespoke crawler — just a strategy + config) indexes a
 * tabular catalog through {@link DumStructuredSourcePlugin}, with the grounding contract holding.
 */
class DumTabularSourcePluginTest {

    private static final String SOURCE = "courses";
    private static final String LISTING = "https://uni.edu/cursos";

    private static final Map<String, String> PAGES = Map.of(LISTING, """
            <html><body>
            <table class="listagem">
              <thead><tr><th>Código</th><th>Curso</th><th>Área</th></tr></thead>
              <tbody>
                <tr><td>CC101</td><td><a href="/curso/CC101">Algoritmos</a></td><td>Computação</td></tr>
                <tr><td>MAT201</td><td><a href="/curso/MAT201">Cálculo</a></td><td></td></tr>
              </tbody>
            </table>
            </body></html>
            """);

    @Test
    void indexesTabularCatalogEndToEndThroughTheSpi() {
        CapturingContext context = new CapturingContext();
        DumStructuredSourcePlugin plugin =
                new TabularPlugin(context, new TabularProvider(new TabularCatalog()));

        plugin.indexAll(SOURCE);

        assertEquals(2, context.items.size());
        assertEquals(1, context.finishedSessions.size());

        TurSNJobItem cc101 = itemById(context, "CC101");
        assertEquals("Algoritmos", cc101.getAttributes().get("title"));
        assertEquals("Computação", cc101.getAttributes().get("area"));
        assertEquals("https://uni.edu/curso/CC101", cc101.getAttributes().get("url"));
        assertEquals(4, cc101.getSpecs().size());

        // MAT201's "Área" cell is empty -> grounded away, absent rather than "".
        TurSNJobItem mat201 = itemById(context, "MAT201");
        assertEquals("Cálculo", mat201.getAttributes().get("title"));
        assertEquals(false, mat201.getAttributes().containsKey("area"));
    }

    private static TurSNJobItem itemById(CapturingContext context, String id) {
        return context.items.stream()
                .map(DumJobItemWithSession::turSNJobItem)
                .filter(item -> id.equals(item.getAttributes().get(TurSNFieldName.ID)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No job item with id " + id));
    }

    // --- A structured source that is just a tabular strategy + a declared schema --------------

    private static final class TabularCatalog implements DumStructuredSource {
        private final DumStructuredSchema schema = DumStructuredSchema.builder()
                .string("code")
                .field(DumStructuredField.builder("title").type(TurSEFieldType.TEXT)
                        .mandatory(true).build())
                .string("url")
                .string("area")
                .build();

        private final DumTabularListingStrategy strategy =
                DumTabularListingStrategy.builder(DumHtmlFetcher.ofPages(PAGES), schema)
                        .listing(LISTING)
                        .rows("table.listagem > tbody > tr")
                        .column("code", 0)
                        .column(DumColumnRule.selector("title", "td:nth-child(2) a"))
                        .column(DumColumnRule.selector("url", "td:nth-child(2) a", "abs:href"))
                        .column("area", 2)
                        .idField("code")
                        .build();

        @Override
        public String getSourceName() {
            return SOURCE;
        }

        @Override
        public String getProviderName() {
            return "STRUCTURED-TABULAR";
        }

        @Override
        public Collection<String> getSiteNames() {
            return List.of("Sample");
        }

        @Override
        public DumStructuredSchema schema() {
            return schema;
        }

        @Override
        public Locale getLocale() {
            return Locale.US;
        }

        @Override
        public List<String> discover() {
            return strategy.discover();
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            return strategy.extract(contentId);
        }
    }

    private static final class TabularProvider implements DumStructuredSourceProvider {
        private final TabularCatalog catalog;

        TabularProvider(TabularCatalog catalog) {
            this.catalog = catalog;
        }

        @Override
        public String getProviderName() {
            return "STRUCTURED-TABULAR";
        }

        @Override
        public Collection<String> sourceNames() {
            return List.of(SOURCE);
        }

        @Override
        public Optional<DumStructuredSource> resolve(String sourceName) {
            return SOURCE.equals(sourceName) ? Optional.of(catalog) : Optional.empty();
        }
    }

    private static final class TabularPlugin extends DumStructuredSourcePlugin {
        private final DumStructuredSourceProvider provider;

        TabularPlugin(DumConnectorContext context, DumStructuredSourceProvider provider) {
            super(context);
            this.provider = provider;
        }

        @Override
        protected DumStructuredSourceProvider provider() {
            return provider;
        }
    }

    private static final class CapturingContext implements DumConnectorContext {
        private final List<DumJobItemWithSession> items = new ArrayList<>();
        private final List<DumConnectorSession> finishedSessions = new ArrayList<>();

        @Override
        public boolean addJobItem(DumJobItemWithSession dumJobItemWithSession) {
            return items.add(dumJobItemWithSession);
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            this.finishedSessions.add(session);
        }

        @Override
        public List<DumConnectorIndexing> getIndexingItem(String objectId, String source,
                String provider) {
            return List.of();
        }

        @Override
        public List<String> getObjectIdByDependency(String source, String provider,
                List<String> dependenciesObjectIdList) {
            return List.of();
        }
    }
}
