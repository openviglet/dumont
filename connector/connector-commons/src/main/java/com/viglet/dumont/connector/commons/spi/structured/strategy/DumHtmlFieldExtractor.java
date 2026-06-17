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
import java.util.List;
import java.util.Optional;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance;

import lombok.extern.slf4j.Slf4j;

/**
 * The <em>extract</em> half a discovery strategy pairs with: fetch a detail page and apply a set of
 * {@link DumFieldRule}s to turn it into a schema-bound {@link DumStructuredRecord} (roadmap
 * <strong>D02</strong>).
 *
 * <p>
 * Each rule maps a declared field to a CSS selector; values flow through the record builder, so the
 * grounding contract still holds — a selector that matches nothing leaves its field absent rather
 * than publishing an empty string. A source built on {@link DumSitemapStrategy} or
 * {@link DumHtmlListingStrategy} discovers detail URLs and hands each to {@link #extract(String)}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public final class DumHtmlFieldExtractor {

    private final DumHtmlFetcher fetcher;
    private final DumStructuredSchema schema;
    private final List<DumFieldRule> rules;

    private DumHtmlFieldExtractor(Builder builder) {
        this.fetcher = builder.fetcher;
        this.schema = builder.schema;
        this.rules = List.copyOf(builder.rules);
    }

    public static Builder builder(DumHtmlFetcher fetcher, DumStructuredSchema schema) {
        return new Builder(fetcher, schema);
    }

    /** Fetch the detail page at {@code url} and extract a record (the URL is the content id). */
    public Optional<DumStructuredRecord> extract(String url) {
        Document document = fetchHtml(url);
        return document == null ? Optional.empty() : Optional.of(extract(url, document));
    }

    /** Extract a record from an already-parsed detail document, using {@code id} as the content id. */
    public DumStructuredRecord extract(String id, Element scope) {
        DumStructuredRecord.Builder record = DumStructuredRecord.builder(id, schema);
        for (DumFieldRule rule : rules) {
            // D16: tag each value with the CSS selector it was read from, so the published document
            // can cite where the field came from.
            record.set(rule.field(), rule.resolve(scope), DumFieldProvenance.css(rule.selector()));
        }
        return record.build();
    }

    private Document fetchHtml(String url) {
        try {
            return fetcher.html(url);
        } catch (IOException e) {
            log.warn("Could not fetch detail page '{}': {}", url, e.getMessage());
            return null;
        }
    }

    public static final class Builder {
        private final DumHtmlFetcher fetcher;
        private final DumStructuredSchema schema;
        private final List<DumFieldRule> rules = new ArrayList<>();

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

        public Builder rule(DumFieldRule rule) {
            this.rules.add(rule);
            return this;
        }

        public Builder text(String field, String selector) {
            return rule(DumFieldRule.text(field, selector));
        }

        public Builder attr(String field, String selector, String attribute) {
            return rule(DumFieldRule.attr(field, selector, attribute));
        }

        public Builder multiText(String field, String selector) {
            return rule(DumFieldRule.multiText(field, selector));
        }

        public DumHtmlFieldExtractor build() {
            if (rules.isEmpty()) {
                throw new IllegalStateException("At least one field rule is required");
            }
            return new DumHtmlFieldExtractor(this);
        }
    }
}
