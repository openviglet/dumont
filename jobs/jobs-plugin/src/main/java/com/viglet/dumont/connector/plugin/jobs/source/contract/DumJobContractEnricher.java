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

package com.viglet.dumont.connector.plugin.jobs.source.contract;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnricher;
import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;
import com.viglet.dumont.connector.plugin.jobs.source.contract.DumJobContractParser.Contract;

import lombok.extern.slf4j.Slf4j;

/**
 * The single canonical contract step of every job record (trabalharei <strong>TRA21</strong>): a D04
 * {@link DumEnricher} that writes {@code contract} in the site's vocabulary.
 *
 * <p>
 * Three inputs, in order: the partner's own value (what the adapter put in {@code contract}: Adzuna's
 * {@code contract_type}, Jooble's {@code type}, JSearch's employment type), then the title, then the
 * text. <strong>The first input that names any contract kind decides</strong>: one kind is the
 * contract; two different kinds ({@code "CLT ou PJ"}) leave it absent and the later inputs are not
 * read — an ambiguous title is not settled by a guess from the description. Keywords are
 * {@link DumJobContractParser}'s.
 *
 * <p>
 * The partner's value is read from the record transients (D117) and
 * {@code extract} leaves {@code contract} off the record (D98), so this step is the only writer and
 * can drop a partner value that names no kind or two.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public final class DumJobContractEnricher implements DumEnricher {

    /** The enricher name, also the provenance tag of the field it writes. */
    public static final String NAME = "job-contract";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord rec) {
        Object partner = rec.transients().get(DumJobsSchema.CONTRACT);
        String[] inputs = { text(partner != null ? partner : rec.values().get(DumJobsSchema.CONTRACT)),
                text(rec.values().get(DumJobsSchema.TITLE)),
                text(rec.values().get(DumJobsSchema.TEXT)) };
        for (String input : inputs) {
            Set<Contract> kinds = kinds(input);
            if (kinds.size() == 1) {
                return Map.of(DumJobsSchema.CONTRACT, kinds.iterator().next().value());
            }
            if (kinds.size() > 1) {
                log.debug("Job '{}' in source '{}': '{}' names {} contract kinds — left absent", rec.id(),
                        source.getSourceName(), input, kinds);
                return Map.of();
            }
        }
        return Map.of();
    }

    /** A value already in the vocabulary is itself; anything else is read for keywords. */
    private static Set<Contract> kinds(String input) {
        if (input == null) {
            return Set.of();
        }
        String folded = DumJobNormalizer.fold(input);
        for (Contract contract : Contract.values()) {
            if (contract.value().equals(folded)) {
                return Set.of(contract);
            }
        }
        return DumJobContractParser.kinds(input);
    }

    private static String text(Object value) {
        if (value instanceof Collection<?> values) {
            value = values.isEmpty() ? null : values.iterator().next();
        }
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
