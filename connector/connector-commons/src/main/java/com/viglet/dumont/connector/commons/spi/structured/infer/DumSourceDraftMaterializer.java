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

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumColumnRule;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumHtmlFetcher;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumTabularListingStrategy;

/**
 * Turns a <strong>confirmed</strong> {@link DumSourceDraft} (roadmap <strong>D13</strong>) into a
 * live {@link DumStructuredSource} that the generic {@code DumStructuredSourcePlugin} can index
 * end-to-end — the same path a hand-written D02 source would take. This is what makes "point the
 * wizard at a catalog, confirm, and it indexes" true: the draft's manifest becomes a
 * {@link DumStructuredSchema} and its per-field hints become D02 strategy rules.
 *
 * <p>
 * Materialization runs only after a human has reviewed the draft — the inference itself never reaches
 * this class. Today it builds {@link DumSourceShape#TABULAR_LISTING} sources (the D13 "done" path);
 * the other shapes are deferred to their own tasks and fail fast with a clear message rather than
 * indexing a half-understood source.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.2
 */
public final class DumSourceDraftMaterializer {

    private DumSourceDraftMaterializer() {
    }

    /**
     * Build an indexable {@link DumStructuredSource} from a confirmed draft.
     *
     * @param draft        the human-confirmed draft
     * @param fetcher      the fetcher the strategy reads through (production HTTP, or in-memory in tests)
     * @param url          the listing URL the source was inferred from
     * @param sourceName   the source name as the connector API will address it
     * @param providerName the provider name reported for routing
     * @param siteNames    the Turing SN sites to publish to
     * @param locale       the document locale
     * @throws IllegalArgumentException when the draft is incomplete (no fields, no row selector)
     * @throws UnsupportedOperationException for shapes not yet materializable
     */
    public static DumStructuredSource materialize(DumSourceDraft draft, DumHtmlFetcher fetcher,
            String url, String sourceName, String providerName, Collection<String> siteNames,
            Locale locale) {
        if (draft == null || draft.shape() != DumSourceShape.TABULAR_LISTING) {
            throw new UnsupportedOperationException(
                    "Only TABULAR_LISTING drafts can be materialized today; got "
                            + (draft == null ? "null" : draft.shape()));
        }
        if (draft.fields().isEmpty()) {
            throw new IllegalArgumentException("A confirmed draft must declare at least one field");
        }
        String rowSelector = draft.config(DumSourceDraft.ROW_SELECTOR);
        if (rowSelector == null || rowSelector.isBlank()) {
            throw new IllegalArgumentException("A tabular draft requires a row selector");
        }

        DumStructuredSchema schema = buildSchema(draft);
        DumTabularListingStrategy.Builder strategy =
                DumTabularListingStrategy.builder(fetcher, schema)
                        .listing(url)
                        .rows(rowSelector)
                        .idField(draft.config(DumSourceDraft.ID_FIELD));
        draft.fields().forEach(field -> strategy.column(toColumnRule(field)));

        return new MaterializedTabularSource(sourceName, providerName, List.copyOf(siteNames),
                locale == null ? Locale.US : locale, schema, strategy.build());
    }

    private static DumStructuredSchema buildSchema(DumSourceDraft draft) {
        DumStructuredSchema.Builder schema = DumStructuredSchema.builder();
        draft.fields().forEach(field -> schema.field(field.toStructuredField()));
        return schema.build();
    }

    /** Map an inferred field's structural hint to a D02 column rule (selector wins over index). */
    private static DumColumnRule toColumnRule(DumInferredField field) {
        if (field.hasSelector()) {
            return field.attribute() == null
                    ? DumColumnRule.selector(field.name(), field.selector())
                    : DumColumnRule.selector(field.name(), field.selector(), field.attribute());
        }
        int index = field.columnIndex() == null ? -1 : field.columnIndex();
        if (index < 0) {
            throw new IllegalArgumentException(
                    "Field '" + field.name() + "' needs a column index or a selector");
        }
        return field.attribute() == null
                ? DumColumnRule.column(field.name(), index)
                : DumColumnRule.column(field.name(), index, field.attribute());
    }

    /** A {@link DumStructuredSource} whose discover/extract are entirely a confirmed tabular strategy. */
    private record MaterializedTabularSource(String sourceName, String providerName,
            Collection<String> siteNames, Locale locale, DumStructuredSchema schema,
            DumTabularListingStrategy strategy) implements DumStructuredSource {

        @Override
        public String getSourceName() {
            return sourceName;
        }

        @Override
        public String getProviderName() {
            return providerName;
        }

        @Override
        public Collection<String> getSiteNames() {
            return siteNames;
        }

        @Override
        public DumStructuredSchema schema() {
            return schema;
        }

        @Override
        public Locale getLocale() {
            return locale;
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
}
