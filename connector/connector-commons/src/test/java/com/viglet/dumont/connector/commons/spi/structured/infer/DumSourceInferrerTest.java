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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.strategy.DumHtmlFetcher;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * Unit tests for roadmap <strong>D13</strong> inference: classification, draft parsing and the
 * grounding contract (decline rather than guess; never fabricate a field).
 */
class DumSourceInferrerTest {

    private static final String URL = "https://uni.edu/cursos";

    private static final Map<String, String> PAGES = Map.of(URL, """
            <html><body>
            <table class="listagem">
              <thead><tr><th>Código</th><th>Curso</th></tr></thead>
              <tbody>
                <tr><td>CC101</td><td><a href="/curso/CC101">Algoritmos</a></td></tr>
              </tbody>
            </table>
            </body></html>
            """);

    private static final String TABULAR_JSON = """
            {"shape":"TABULAR_LISTING","confidence":0.82,"idField":"code",
             "rowSelector":"table.listagem > tbody > tr",
             "fields":[
               {"name":"code","type":"STRING","mandatory":true,"columnIndex":0},
               {"name":"title","type":"TEXT","selector":"td:nth-child(2) a"},
               {"name":"url","type":"STRING","selector":"td:nth-child(2) a","attribute":"abs:href"},
               {"name":"","type":"STRING","columnIndex":9}
             ],
             "notes":"SIGAA-style catalog"}
            """;

    @Test
    void classifiesTabularAndProposesAnEditableDraft() {
        AtomicReference<String> seenSample = new AtomicReference<>();
        DumSourceInferrer inferrer = new DumSourceInferrer(DumHtmlFetcher.ofPages(PAGES),
                (prompt, sample) -> {
                    seenSample.set(sample);
                    return Optional.of(TABULAR_JSON);
                });

        DumSourceDraft draft = inferrer.infer(URL);

        assertEquals(DumSourceShape.TABULAR_LISTING, draft.shape());
        assertEquals(0.82, draft.confidence(), 1e-9);
        assertEquals("code", draft.config(DumSourceDraft.ID_FIELD));
        assertEquals("table.listagem > tbody > tr", draft.config(DumSourceDraft.ROW_SELECTOR));

        // The blank-named field is dropped — grounding, not a placeholder.
        assertEquals(3, draft.fields().size());
        DumInferredField title = draft.fields().get(1);
        assertEquals("title", title.name());
        assertEquals(TurSEFieldType.TEXT, title.type());
        assertEquals("td:nth-child(2) a", title.selector());
        DumInferredField url = draft.fields().get(2);
        assertEquals("abs:href", url.attribute());
        assertEquals(Integer.valueOf(0), draft.fields().get(0).columnIndex());

        // The model was handed computed structural signals + markup, never asked for values.
        assertTrue(seenSample.get().contains("tables=1"), "sample carries structural signals");
        assertTrue(seenSample.get().contains("listagem"), "sample carries the markup excerpt");
    }

    @Test
    void unfetchableUrlDeclinesWithUnknown() {
        DumSourceInferrer inferrer = new DumSourceInferrer(DumHtmlFetcher.ofPages(Map.of()),
                (prompt, sample) -> {
                    throw new AssertionError("model must not be called when the URL cannot be fetched");
                });

        DumSourceDraft draft = inferrer.infer("https://missing.example/none");

        assertEquals(DumSourceShape.UNKNOWN, draft.shape());
        assertTrue(draft.fields().isEmpty());
    }

    @Test
    void noModelAnswerDeclinesWithUnknown() {
        DumSourceInferrer inferrer = new DumSourceInferrer(DumHtmlFetcher.ofPages(PAGES),
                (prompt, sample) -> Optional.empty());

        assertEquals(DumSourceShape.UNKNOWN, inferrer.infer(URL).shape());
    }

    @Test
    void unparseableAnswerDeclinesWithUnknown() {
        DumSourceInferrer inferrer = new DumSourceInferrer(DumHtmlFetcher.ofPages(PAGES),
                (prompt, sample) -> Optional.of("I think this is a table, probably."));

        assertEquals(DumSourceShape.UNKNOWN, inferrer.infer(URL).shape());
    }

    @Test
    void modelClassifiedUnknownStaysUnknownWithNoFields() {
        DumSourceInferrer inferrer = new DumSourceInferrer(DumHtmlFetcher.ofPages(PAGES),
                (prompt, sample) -> Optional.of(
                        "{\"shape\":\"UNKNOWN\",\"fields\":[{\"name\":\"x\",\"columnIndex\":0}]}"));

        DumSourceDraft draft = inferrer.infer(URL);
        assertEquals(DumSourceShape.UNKNOWN, draft.shape());
        assertTrue(draft.fields().isEmpty(), "an UNKNOWN draft carries no proposed fields");
    }

    @Test
    void toleratesJsonFencedInProse() {
        DumSourceInferrer inferrer = new DumSourceInferrer(DumHtmlFetcher.ofPages(PAGES),
                (prompt, sample) -> Optional.of(
                        "Here is the draft:\n```json\n" + TABULAR_JSON + "\n```\nHope that helps!"));

        assertEquals(DumSourceShape.TABULAR_LISTING, inferrer.infer(URL).shape());
    }

    @Test
    void unknownFieldTypeFallsBackToStringRatherThanFailing() {
        DumSourceInferrer inferrer = new DumSourceInferrer(DumHtmlFetcher.ofPages(PAGES),
                (prompt, sample) -> Optional.of(
                        "{\"shape\":\"TABULAR_LISTING\",\"rowSelector\":\"tr\","
                                + "\"fields\":[{\"name\":\"code\",\"type\":\"WIDGET\",\"columnIndex\":0}]}"));

        DumSourceDraft draft = inferrer.infer(URL);
        assertEquals(1, draft.fields().size());
        assertSame(TurSEFieldType.STRING, draft.fields().get(0).type());
        assertNull(draft.fields().get(0).selector());
    }

    @Test
    void blankUrlDeclinesWithoutCallingTheModel() {
        DumSourceInferrer inferrer = new DumSourceInferrer(DumHtmlFetcher.ofPages(PAGES),
                (prompt, sample) -> {
                    throw new AssertionError("model must not be called for a blank URL");
                });

        assertEquals(DumSourceShape.UNKNOWN, inferrer.infer("  ").shape());
        assertEquals(List.of(), inferrer.infer("").fields());
    }
}
