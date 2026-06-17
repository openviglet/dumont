/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */

package com.viglet.dumont.connector.commons.spi.structured.infer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourcePlugin;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenance;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumHtmlFetcher;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.sn.field.TurSNFieldName;

/**
 * The roadmap <strong>D13</strong> "done" path: a tabular catalog is inferred into an editable draft
 * that, once confirmed, is materialized and indexed end-to-end through the generic
 * {@link DumStructuredSourcePlugin} — with the grounding contract still holding. Nothing here is
 * hand-authored: the schema and the strategy are built from the inferred draft.
 */
class DumInferredSourceIndexingTest {

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

    private static final String DRAFT_JSON = """
            {"shape":"TABULAR_LISTING","confidence":0.9,"idField":"code",
             "rowSelector":"table.listagem > tbody > tr",
             "fields":[
               {"name":"code","type":"STRING","mandatory":true,"columnIndex":0},
               {"name":"title","type":"TEXT","mandatory":true,"selector":"td:nth-child(2) a"},
               {"name":"url","type":"STRING","selector":"td:nth-child(2) a","attribute":"abs:href"},
               {"name":"area","type":"STRING","facet":true,"columnIndex":2}
             ]}
            """;

    @Test
    void inferThenConfirmThenIndexEndToEnd() {
        DumHtmlFetcher fetcher = DumHtmlFetcher.ofPages(PAGES);

        // 1. Infer a draft from the live sample (model stubbed with a realistic JSON answer).
        DumSourceInferrer inferrer = new DumSourceInferrer(fetcher,
                (prompt, sample) -> Optional.of(DRAFT_JSON));
        DumSourceDraft draft = inferrer.infer(LISTING);
        assertEquals(DumSourceShape.TABULAR_LISTING, draft.shape());

        // 2. A human confirms it -> materialize into a live structured source.
        DumStructuredSource source = DumSourceDraftMaterializer.materialize(draft, fetcher, LISTING,
                SOURCE, "STRUCTURED-TABULAR", List.of("Sample"), Locale.US);

        // 3. Index end-to-end through the generic plugin.
        CapturingContext context = new CapturingContext();
        DumStructuredSourcePlugin plugin = new InferredPlugin(context, new SingleProvider(source));
        plugin.indexAll(SOURCE);

        assertEquals(2, context.items.size());
        assertEquals(1, context.finishedSessions.size());

        TurSNJobItem cc101 = itemById(context, "CC101");
        assertEquals("Algoritmos", cc101.getAttributes().get("title"));
        assertEquals("Computação", cc101.getAttributes().get("area"));
        assertEquals("https://uni.edu/curso/CC101", cc101.getAttributes().get("url"));
        // declared schema fields + the reserved D16 provenance spec carried with the document.
        assertEquals(5, cc101.getSpecs().size());
        assertTrue(cc101.getSpecs().stream()
                .anyMatch(spec -> DumProvenance.FIELD.equals(spec.getName())));

        // MAT201's empty "Área" cell is grounded away — absent, never "".
        TurSNJobItem mat201 = itemById(context, "MAT201");
        assertEquals("Cálculo", mat201.getAttributes().get("title"));
        assertFalse(mat201.getAttributes().containsKey("area"));
    }

    @Test
    void unknownDraftIsNotMaterializable() {
        assertThrows(UnsupportedOperationException.class,
                () -> DumSourceDraftMaterializer.materialize(DumSourceDraft.unknown("nope"),
                        DumHtmlFetcher.ofPages(PAGES), LISTING, SOURCE, "P", List.of("S"), Locale.US));
    }

    private static TurSNJobItem itemById(CapturingContext context, String id) {
        return context.items.stream()
                .map(DumJobItemWithSession::turSNJobItem)
                .filter(item -> id.equals(item.getAttributes().get(TurSNFieldName.ID)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No job item with id " + id));
    }

    private static final class SingleProvider implements DumStructuredSourceProvider {
        private final DumStructuredSource source;

        SingleProvider(DumStructuredSource source) {
            this.source = source;
        }

        @Override
        public String getProviderName() {
            return source.getProviderName();
        }

        @Override
        public Collection<String> sourceNames() {
            return List.of(source.getSourceName());
        }

        @Override
        public Optional<DumStructuredSource> resolve(String sourceName) {
            return source.getSourceName().equals(sourceName) ? Optional.of(source) : Optional.empty();
        }
    }

    private static final class InferredPlugin extends DumStructuredSourcePlugin {
        private final DumStructuredSourceProvider provider;

        InferredPlugin(DumConnectorContext context, DumStructuredSourceProvider provider) {
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
