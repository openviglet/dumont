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

package com.viglet.dumont.connector.commons.spi.structured.recipe;

import java.util.Map;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnricher;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumNormalizationEnricher;

/**
 * A {@link DumEnricher} that applies a versioned {@link DumTaxonomy} resolved from a
 * {@link DumRecipeLibrary} <em>by reference</em>, at enrichment time (roadmap <strong>D19</strong>).
 *
 * <p>
 * It is the runtime side of a recipe's {@link DumTaxonomyApplication}: rather than capturing the
 * taxonomy table when the enrichment chain is built, it looks the taxonomy up in the library on
 * every record. A taxonomy bump (a new version registered for the same name, or the
 * {@link DumRecipeRef#LATEST} target advancing) therefore re-applies immediately to every source
 * whose chain holds this enricher — the chain itself never has to be rebuilt.
 *
 * <p>
 * The actual mapping reuses {@link DumNormalizationEnricher#lookup} for the current version's table,
 * so the grounding contract is identical: an unmapped raw value is omitted, never fabricated.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumTaxonomyEnricher implements DumEnricher {

    private final DumRecipeLibrary library;
    private final DumTaxonomyApplication application;

    DumTaxonomyEnricher(DumRecipeLibrary library, DumTaxonomyApplication application) {
        this.library = library;
        this.application = application;
    }

    @Override
    public String name() {
        return application.enricherName();
    }

    @Override
    public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord record) {
        DumTaxonomy taxonomy = library.taxonomy(application.taxonomy()).orElse(null);
        if (taxonomy == null) {
            // The recipe references a taxonomy the library does not (yet) hold — contribute nothing
            // rather than fail the record. Grounding-safe: a missing map cannot fabricate a value.
            return Map.of();
        }
        return DumNormalizationEnricher
                .lookup(name(), application.sourceField(), application.targetField(), taxonomy.table())
                .enrich(source, record);
    }
}
