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

package com.viglet.dumont.connector.commons.spi.structured.infer;

import java.io.IOException;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.nodes.Document;

import com.viglet.dumont.connector.commons.spi.structured.strategy.DumHtmlFetcher;
import com.viglet.turing.commons.se.field.TurSEFieldType;

import lombok.extern.slf4j.Slf4j;

/**
 * The AI-assisted source inference (roadmap <strong>D13</strong>): given a URL, fetch a sample,
 * classify the source into one of the D02 {@link DumSourceShape shapes} and propose an editable
 * {@link DumSourceDraft} — a strategy config plus a field manifest. The draft is a starting point a
 * human edits and confirms; it never auto-publishes.
 *
 * <p>
 * The orchestration is pure and unit-testable: it {@link DumHtmlFetcher fetches} a sample, computes a
 * few deterministic structural signals (does the page have a {@code <table>}? a sitemap link? how
 * many anchors?), builds a prompt and hands it to the {@link DumSourceInferenceModel} seam, then
 * parses the model's JSON into a draft. No model wiring lives here — the production model adapter
 * reuses Turing's LLM surface (see {@code connector-app}).
 *
 * <p>
 * <strong>Grounding.</strong> The model proposes <em>structure</em> (shape, selectors, field types),
 * never content — actual values always come from the live extraction. Any failure (unfetchable URL,
 * no model answer, unparseable JSON) degrades to a {@link DumSourceShape#UNKNOWN} draft; the
 * inference declines rather than guessing, and never fills a field it could not ground.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.2
 */
@Slf4j
public final class DumSourceInferrer {

    /** Cap on the markup excerpt handed to the model — enough to classify, bounded for cost. */
    private static final int MAX_SAMPLE_CHARS = 12_000;

    static final String SYSTEM_PROMPT = """
            You are a data-integration analyst classifying the STRUCTURE of a web source so it can be \
            indexed by a declarative connector. You are given a URL and an HTML/XML sample with a few \
            computed signals.

            Classify the source into exactly one shape:
            - TABULAR_LISTING: a <table> catalog where each row is a self-contained record \
              (map columns to fields).
            - HTML_LISTING: a listing page whose links point to per-item detail pages.
            - SITEMAP: a sitemap.xml / sitemap index of URLs.
            - UNKNOWN: the shape cannot be determined — prefer this over guessing.

            Then propose a draft FIELD MANIFEST. For each field give: name (snake_case), type \
            (one of STRING, TEXT, INT, LONG, DATE, BOOL), multiValued (bool), facet (bool), \
            mandatory (bool), and the STRUCTURAL hint to read it: for TABULAR_LISTING a 0-based \
            "columnIndex" (and optional "attribute" like "abs:href"), otherwise a CSS "selector" \
            (and optional "attribute"). Name the "idField" (the field that identifies a record) and, \
            for TABULAR_LISTING, the "rowSelector" (CSS selector for data rows).

            HARD RULES:
            - Propose STRUCTURE only (which column/selector, which type). NEVER output field values \
              or sample data — content always comes from the live source, never from you.
            - If you cannot ground a field, omit it. Do not invent placeholder/default fields.
            - Reply with a SINGLE JSON object and nothing else, in this exact form:
            {"shape":"TABULAR_LISTING","confidence":0.0-1.0,"idField":"code",\
            "rowSelector":"table.listagem > tbody > tr",\
            "fields":[{"name":"code","type":"STRING","multiValued":false,"facet":false,\
            "mandatory":true,"columnIndex":0,"selector":null,"attribute":null,"description":null}],\
            "notes":"short rationale"}
            """;

    private final DumHtmlFetcher fetcher;
    private final DumSourceInferenceModel model;

    public DumSourceInferrer(DumHtmlFetcher fetcher, DumSourceInferenceModel model) {
        if (fetcher == null || model == null) {
            throw new IllegalArgumentException("A fetcher and a model are required");
        }
        this.fetcher = fetcher;
        this.model = model;
    }

    /**
     * Infer an editable draft for {@code url}. Always returns a draft — an
     * {@link DumSourceShape#UNKNOWN} one when the source cannot be fetched, the model gives no
     * answer, or the answer is unparseable.
     */
    public DumSourceDraft infer(String url) {
        if (url == null || url.isBlank()) {
            return DumSourceDraft.unknown("No URL supplied");
        }
        Document document = fetch(url);
        if (document == null) {
            return DumSourceDraft.unknown("Could not fetch the source URL");
        }
        String sample = buildSample(url, document);
        Optional<String> completion = model.infer(SYSTEM_PROMPT, sample);
        if (completion.isEmpty()) {
            return DumSourceDraft.unknown("The inference model returned no answer");
        }
        return parse(completion.get());
    }

    private Document fetch(String url) {
        try {
            // A sitemap is XML; everything else parses fine as HTML. Try HTML first, fall back to
            // XML only when the URL clearly points at a sitemap.
            if (url.toLowerCase().endsWith(".xml") || url.toLowerCase().contains("sitemap")) {
                return fetcher.xml(url);
            }
            return fetcher.html(url);
        } catch (IOException | RuntimeException e) {
            log.warn("Source inference could not fetch '{}': {}", url, e.getMessage());
            return null;
        }
    }

    /** A compact, model-ready sample: the URL, computed structural signals, then a bounded excerpt. */
    private String buildSample(String url, Document document) {
        int tables = document.select("table").size();
        int rows = document.select("table tr").size();
        int anchors = document.select("a[href]").size();
        boolean sitemap = !document.select("urlset, sitemapindex, loc").isEmpty();

        String body = document.outerHtml();
        if (body.length() > MAX_SAMPLE_CHARS) {
            body = body.substring(0, MAX_SAMPLE_CHARS);
        }
        return new StringBuilder()
                .append("URL: ").append(url).append('\n')
                .append("Signals: tables=").append(tables)
                .append(", tableRows=").append(rows)
                .append(", anchors=").append(anchors)
                .append(", sitemapElements=").append(sitemap).append('\n')
                .append("Markup excerpt:\n").append(body)
                .toString();
    }

    /** Parse the model's JSON completion into a grounded draft, tolerating prose around the object. */
    private DumSourceDraft parse(String completion) {
        try {
            JSONObject json = new JSONObject(extractJsonObject(completion));
            DumSourceShape shape = DumSourceShape.fromLabel(json.optString("shape", null));
            if (shape == DumSourceShape.UNKNOWN) {
                return DumSourceDraft.unknown(json.optString("notes", "Model classified the source as UNKNOWN"));
            }
            DumSourceDraft.Builder draft = DumSourceDraft.builder(shape)
                    .confidence(json.optDouble("confidence", 0.0))
                    .notes(json.optString("notes", null))
                    .config(DumSourceDraft.ROW_SELECTOR, json.optString("rowSelector", null))
                    .config(DumSourceDraft.ID_FIELD, json.optString("idField", null));

            JSONArray fields = json.optJSONArray("fields");
            if (fields != null) {
                for (int i = 0; i < fields.length(); i++) {
                    parseField(fields.optJSONObject(i)).ifPresent(draft::field);
                }
            }
            return draft.build();
        } catch (JSONException e) {
            log.warn("Source inference returned unparseable JSON — declining: {}", e.getMessage());
            return DumSourceDraft.unknown("The inference model returned an unparseable answer");
        }
    }

    private Optional<DumInferredField> parseField(JSONObject field) {
        if (field == null) {
            return Optional.empty();
        }
        String name = field.optString("name", null);
        if (name == null || name.isBlank()) {
            // Grounding: a field with no name is dropped, never published as a placeholder.
            return Optional.empty();
        }
        Integer columnIndex = field.has("columnIndex") && !field.isNull("columnIndex")
                ? field.optInt("columnIndex")
                : null;
        return Optional.of(new DumInferredField(
                name.trim(),
                parseType(field.optString("type", null)),
                field.optBoolean("multiValued", false),
                field.optBoolean("facet", false),
                field.optBoolean("mandatory", false),
                blankToNull(field.optString("description", null)),
                columnIndex,
                blankToNull(field.optString("selector", null)),
                blankToNull(field.optString("attribute", null))));
    }

    private static TurSEFieldType parseType(String type) {
        if (type == null || type.isBlank()) {
            return TurSEFieldType.STRING;
        }
        try {
            return TurSEFieldType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TurSEFieldType.STRING;
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /** Pull the first {@code { ... }} object out of a completion that may be fenced or prose-wrapped. */
    private static String extractJsonObject(String completion) {
        int start = completion.indexOf('{');
        int end = completion.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return completion.substring(start, end + 1);
        }
        return completion;
    }
}
