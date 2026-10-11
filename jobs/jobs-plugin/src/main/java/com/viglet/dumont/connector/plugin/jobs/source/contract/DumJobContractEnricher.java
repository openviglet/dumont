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

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnricher;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumFallbackEnricher;
import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;
import com.viglet.dumont.connector.plugin.jobs.source.contract.DumJobContractParser.Contract;

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
 * It is a {@link DumFallbackEnricher} declaration (D103) whose stop rule keeps the two-kinds case
 * from falling through. The partner's value is read from the record transients (D117) and
 * {@code extract} leaves {@code contract} off the record (D98), so this step is the only writer and
 * can drop a partner value that names no kind or two.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumJobContractEnricher implements DumEnricher {

    /** The enricher name, also the provenance tag of the field it writes. */
    public static final String NAME = "job-contract";

    /** The declaration (D103): the inputs in order, read for kinds, an ambiguous one stopping it. */
    private final DumEnricher fallback = DumFallbackEnricher.builder(NAME, DumJobsSchema.CONTRACT)
            .input(DumJobsSchema.CONTRACT, 1.0).input(DumJobsSchema.TITLE, 0.8).input(DumJobsSchema.TEXT, 0.6)
            .parser(text -> {
                Set<Contract> kinds = kinds(text);
                return kinds.size() == 1 ? Optional.of(kinds.iterator().next().value()) : Optional.empty();
            })
            .stopOn(text -> kinds(text).size() > 1)
            .build();

    @Override
    public String name() {
        return NAME;
    }

    /**
     * The contract the first deciding input names, with provenance naming that input (D101): the
     * partner's field, the title or the text.
     */
    @Override
    public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord rec) {
        return fallback.enrich(source, rec);
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
}
