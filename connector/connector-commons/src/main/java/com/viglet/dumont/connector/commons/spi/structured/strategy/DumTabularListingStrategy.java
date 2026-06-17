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

package com.viglet.dumont.connector.commons.spi.structured.strategy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance;

import lombok.extern.slf4j.Slf4j;

/**
 * The strategy the CSS-selector model lacks today: parse a {@code <table>}-style catalog listing
 * (the SIGAA shape) where each row is a self-contained record, mapping columns to schema fields
 * (roadmap <strong>D02</strong>).
 *
 * <p>
 * Unlike {@link DumSitemapStrategy}/{@link DumHtmlListingStrategy}, a tabular listing carries the
 * data inline, so this strategy does <em>both</em> halves of the SPI: {@link #discover()} returns
 * one id per row and {@link #extract(String)} returns the row's
 * {@link DumStructuredRecord}. The table is fetched and parsed once, then cached. Each row's id comes
 * from the {@link Builder#idField designated column}; rows whose mapped cells are all blank (header
 * rows, separators) are skipped, and every value flows through the grounding contract so absent
 * cells never publish as {@code ""}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public final class DumTabularListingStrategy {

    private static final String CELL_SELECTOR = "> th, > td";

    private final DumHtmlFetcher fetcher;
    private final DumStructuredSchema schema;
    private final List<String> listingUrls;
    private final String rowSelector;
    private final List<DumColumnRule> columnRules;
    private final String idField;

    private Map<String, DumStructuredRecord> recordsById;

    private DumTabularListingStrategy(Builder builder) {
        this.fetcher = builder.fetcher;
        this.schema = builder.schema;
        this.listingUrls = List.copyOf(builder.listingUrls);
        this.rowSelector = builder.rowSelector;
        this.columnRules = List.copyOf(builder.columnRules);
        this.idField = builder.idField;
    }

    public static Builder builder(DumHtmlFetcher fetcher, DumStructuredSchema schema) {
        return new Builder(fetcher, schema);
    }

    /** The id of every row that produced a non-empty record, in table order. */
    public List<String> discover() {
        return new ArrayList<>(records().keySet());
    }

    /** The record for a row id, or empty when no such row was parsed. */
    public Optional<DumStructuredRecord> extract(String contentId) {
        return Optional.ofNullable(records().get(contentId));
    }

    private synchronized Map<String, DumStructuredRecord> records() {
        if (recordsById != null) {
            return recordsById;
        }
        Map<String, DumStructuredRecord> built = new LinkedHashMap<>();
        int rowNumber = 0;
        for (String url : listingUrls) {
            Document document = fetchHtml(url);
            if (document == null) {
                continue;
            }
            for (Element row : document.select(rowSelector)) {
                rowNumber++;
                parseRow(row, rowNumber).ifPresent(record -> built.put(record.id(), record));
            }
        }
        recordsById = built;
        return recordsById;
    }

    private Optional<DumStructuredRecord> parseRow(Element row, int rowNumber) {
        Elements cells = cells(row);
        Map<String, Object> values = new LinkedHashMap<>();
        columnRules.forEach(rule -> values.put(rule.field(), rule.resolve(row, cells)));
        String id = resolveId(values, rowNumber);
        DumStructuredRecord.Builder record = DumStructuredRecord.builder(id, schema);
        // D16: tag each cell value with the column locator it was read from, so the published
        // document can cite where the field came from.
        columnRules.forEach(rule -> record.set(rule.field(), values.get(rule.field()),
                DumFieldProvenance.tabular(rule.locator())));
        DumStructuredRecord built = record.build();
        // Header rows / separators map to nothing — skip them rather than emit an id-only document.
        return built.values().isEmpty() ? Optional.empty() : Optional.of(built);
    }

    private String resolveId(Map<String, Object> values, int rowNumber) {
        if (idField != null) {
            String id = firstNonBlank(values.get(idField));
            if (id != null) {
                return id;
            }
        }
        return "row-" + rowNumber;
    }

    private static String firstNonBlank(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim)
                    .filter(s -> !s.isEmpty()).findFirst().orElse(null);
        }
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static Elements cells(Element row) {
        Elements direct = row.select(CELL_SELECTOR);
        return direct.isEmpty() ? row.select("th, td") : direct;
    }

    private Document fetchHtml(String url) {
        try {
            return fetcher.html(url);
        } catch (IOException e) {
            log.warn("Could not fetch tabular listing '{}': {}", url, e.getMessage());
            return null;
        }
    }

    public static final class Builder {
        private final DumHtmlFetcher fetcher;
        private final DumStructuredSchema schema;
        private final List<String> listingUrls = new ArrayList<>();
        private final List<DumColumnRule> columnRules = new ArrayList<>();
        private String rowSelector;
        private String idField;

        private Builder(DumHtmlFetcher fetcher, DumStructuredSchema schema) {
            if (fetcher == null) {
                throw new IllegalArgumentException("A fetcher is required");
            }
            if (schema == null) {
                throw new IllegalArgumentException("A schema is required");
            }
            this.fetcher = fetcher;
            this.schema = schema;
        }

        public Builder listing(String url) {
            this.listingUrls.add(url);
            return this;
        }

        public Builder listings(Iterable<String> urls) {
            urls.forEach(this.listingUrls::add);
            return this;
        }

        /** The CSS selector for the data rows (e.g. {@code "table.listagem > tbody > tr"}). */
        public Builder rows(String rowSelector) {
            this.rowSelector = rowSelector;
            return this;
        }

        public Builder column(DumColumnRule rule) {
            this.columnRules.add(rule);
            return this;
        }

        /** Map the cell at {@code columnIndex} (text) to {@code field}. */
        public Builder column(String field, int columnIndex) {
            return column(DumColumnRule.column(field, columnIndex));
        }

        /**
         * The field whose value identifies each row. When unset, or blank for a given row, a
         * positional {@code row-N} id is used instead.
         */
        public Builder idField(String idField) {
            this.idField = idField;
            return this;
        }

        public DumTabularListingStrategy build() {
            if (listingUrls.isEmpty()) {
                throw new IllegalStateException("At least one listing URL is required");
            }
            if (rowSelector == null || rowSelector.isBlank()) {
                throw new IllegalStateException("A row selector is required");
            }
            if (columnRules.isEmpty()) {
                throw new IllegalStateException("At least one column rule is required");
            }
            return new DumTabularListingStrategy(this);
        }
    }
}
