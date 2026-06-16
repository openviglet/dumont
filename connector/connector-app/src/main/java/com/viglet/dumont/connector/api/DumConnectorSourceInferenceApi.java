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

package com.viglet.dumont.connector.api;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourcePlugin;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;
import com.viglet.dumont.connector.commons.spi.structured.infer.DumSourceDraft;
import com.viglet.dumont.connector.commons.spi.structured.infer.DumSourceDraftMaterializer;
import com.viglet.dumont.connector.commons.spi.structured.infer.DumSourceInferrer;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumInMemoryPreviewSnapshotStore;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumPreviewSnapshotStore;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumStructuredPreview;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumHtmlFetcher;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumJsoupFetcher;
import com.viglet.dumont.connector.config.DumTuringCsrfService;
import com.viglet.dumont.connector.infer.DumTuringSourceInferenceModel;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.extern.slf4j.Slf4j;

/**
 * AI-assisted source inference (roadmap <strong>D13</strong>): given a URL, classify the source shape
 * and propose an editable {@link DumSourceDraft} (strategy config + field manifest), then — only
 * after a human confirms the draft — materialize it into a structured source and index it end-to-end.
 *
 * <p>
 * The inference reuses Turing's existing LLM surface via the shared {@code RestClient} (no new model
 * wiring; see {@link DumTuringSourceInferenceModel}). The draft <strong>never auto-publishes</strong>
 * — {@code /infer} only proposes; {@code /infer/index} runs only on the confirmed draft posted back.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.2
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v2/connector/source/infer")
@Tag(name = "Source Inference", description = "AI-assisted source shape + field-manifest inference")
@Slf4j
public class DumConnectorSourceInferenceApi {

    private static final String PROVIDER_NAME = "STRUCTURED-INFERRED";

    private final DumConnectorContext connectorContext;
    private final DumSourceInferrer inferrer;
    private final DumHtmlFetcher fetcher;
    // App-wide baseline store (the controller is a singleton): an index run captures a snapshot a
    // later preview of the same source diffs against. Survives the connector's lifetime, not restarts.
    private final DumPreviewSnapshotStore previewSnapshotStore = new DumInMemoryPreviewSnapshotStore();

    public DumConnectorSourceInferenceApi(RestClient restClient, DumTuringCsrfService csrfService,
            DumConnectorContext connectorContext) {
        this.connectorContext = connectorContext;
        this.fetcher = new DumJsoupFetcher();
        this.inferrer = new DumSourceInferrer(fetcher,
                new DumTuringSourceInferenceModel(restClient, csrfService));
    }

    @Operation(summary = "Infer an editable source draft (shape + field manifest) from a URL")
    @PostMapping
    public ResponseEntity<DumSourceDraft> infer(@RequestBody InferRequest request) {
        if (request == null || request.url() == null || request.url().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(DumSourceDraft.unknown("A URL is required"));
        }
        return ResponseEntity.ok(inferrer.infer(request.url().trim()));
    }

    @Operation(summary = "Index a human-confirmed draft end-to-end (never auto-published)")
    @PostMapping("/index")
    public ResponseEntity<IndexResult> index(@RequestBody InferIndexRequest request) {
        if (request == null || request.draft() == null || request.url() == null
                || request.url().isBlank() || request.sourceName() == null
                || request.sourceName().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new IndexResult(false, null, "url, sourceName and a confirmed draft are required"));
        }
        Collection<String> sites = request.siteNames() == null ? List.of() : request.siteNames();
        try {
            DumStructuredSource source = DumSourceDraftMaterializer.materialize(request.draft(),
                    fetcher, request.url().trim(), request.sourceName().trim(), PROVIDER_NAME, sites,
                    resolveLocale(request.locale()));

            new InferredPlugin(connectorContext, source, previewSnapshotStore)
                    .indexAll(source.getSourceName());

            log.info("Indexed confirmed inferred source '{}' to sites {}", source.getSourceName(), sites);
            return ResponseEntity.ok(new IndexResult(true, source.getSourceName(),
                    "Indexing started for the confirmed source"));
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new IndexResult(false, request.sourceName(), e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Failed to index confirmed inferred source '{}'", request.sourceName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new IndexResult(false, request.sourceName(),
                            "Indexing failed: " + e.getMessage()));
        }
    }

    @Operation(summary = "Dry-run preview of a confirmed draft: coverage + schema/document diff, no write")
    @PostMapping("/preview")
    public ResponseEntity<PreviewResult> preview(@RequestBody InferIndexRequest request,
            @RequestParam(name = "sampleSize", defaultValue = "20") int sampleSize) {
        if (request == null || request.draft() == null || request.url() == null
                || request.url().isBlank() || request.sourceName() == null
                || request.sourceName().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new PreviewResult(false, null, "url, sourceName and a confirmed draft are required"));
        }
        Collection<String> sites = request.siteNames() == null ? List.of() : request.siteNames();
        try {
            DumStructuredSource source = DumSourceDraftMaterializer.materialize(request.draft(),
                    fetcher, request.url().trim(), request.sourceName().trim(), PROVIDER_NAME, sites,
                    resolveLocale(request.locale()));

            DumStructuredPreview preview = new InferredPlugin(connectorContext, source,
                    previewSnapshotStore).preview(source.getSourceName(), sampleSize);

            return ResponseEntity.ok(new PreviewResult(true, preview, null));
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new PreviewResult(false, null, e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Failed to preview confirmed inferred source '{}'", request.sourceName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PreviewResult(false, null, "Preview failed: " + e.getMessage()));
        }
    }

    private static Locale resolveLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return Locale.US;
        }
        return Locale.forLanguageTag(locale.trim().replace('_', '-'));
    }

    /** A {@link DumStructuredSourcePlugin} over a single confirmed, materialized source. */
    private static final class InferredPlugin extends DumStructuredSourcePlugin
            implements DumStructuredSourceProvider {
        private final DumStructuredSource source;

        InferredPlugin(DumConnectorContext context, DumStructuredSource source,
                DumPreviewSnapshotStore previewSnapshotStore) {
            super(context, null, previewSnapshotStore);
            this.source = source;
        }

        @Override
        protected DumStructuredSourceProvider provider() {
            return this;
        }

        @Override
        public Collection<String> sourceNames() {
            return List.of(source.getSourceName());
        }

        @Override
        public Optional<DumStructuredSource> resolve(String sourceName) {
            return source.getSourceName().equals(sourceName) ? Optional.of(source) : Optional.empty();
        }

        @Override
        public String getProviderName() {
            return source.getProviderName();
        }
    }

    /** Request body of {@code POST /infer}. */
    public record InferRequest(String url) {
    }

    /** Request body of {@code POST /infer/index} — the human-confirmed draft + target. */
    public record InferIndexRequest(String url, String sourceName, List<String> siteNames,
            String locale, DumSourceDraft draft) {
    }

    /** Response of {@code POST /infer/index}. */
    public record IndexResult(boolean success, String source, String message) {
    }

    /** Response of {@code POST /infer/preview} — the dry-run report (or an error). */
    public record PreviewResult(boolean success, DumStructuredPreview preview, String message) {
    }
}
