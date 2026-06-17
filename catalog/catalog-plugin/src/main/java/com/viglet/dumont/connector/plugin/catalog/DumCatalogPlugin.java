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

package com.viglet.dumont.connector.plugin.catalog;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourcePlugin;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;
import com.viglet.dumont.connector.plugin.catalog.source.DumCatalogSourceProvider;

/**
 * Catalog bridge (sidecar) connector — roadmap <strong>D10</strong> / cursarei <strong>V05.1</strong>.
 *
 * <p>
 * A thin {@link DumStructuredSourcePlugin} over the structured-source SPI: it ingests the
 * already-normalized catalog records the external (Python) engine produces — one source per
 * institution — and republishes each to Turing under the grounding contract. It deliberately does no
 * crawling or extraction; the engine keeps its best-in-class anti-WAF collection, while Dumont gets
 * the user-facing search/RAG wins live fast. Per-institution sources later migrate to native
 * declarative strategies one wave at a time (D11), gated on parity.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component("catalog")
public class DumCatalogPlugin extends DumStructuredSourcePlugin {

    /** Provider name reported for routing and as the document {@code source_apps}. */
    public static final String PROVIDER_NAME = "CATALOG-BRIDGE";

    private final DumCatalogSourceProvider provider;

    public DumCatalogPlugin(DumConnectorContext dumConnectorContext,
            DumCatalogSourceProvider provider) {
        super(dumConnectorContext);
        this.provider = provider;
    }

    @Override
    protected DumStructuredSourceProvider provider() {
        return provider;
    }
}
