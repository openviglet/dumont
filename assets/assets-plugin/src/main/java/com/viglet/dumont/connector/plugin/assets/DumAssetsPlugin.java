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

package com.viglet.dumont.connector.plugin.assets;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourcePlugin;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnrichmentChain;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumPreviewSnapshotStore;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenanceHistoryStore;
import com.viglet.dumont.connector.plugin.assets.source.DumAssetsSourceProvider;
import com.viglet.dumont.connector.plugin.assets.source.DumAssetsStructuredSource;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.sn.field.TurSNFieldName;

/**
 * Filesystem Assets connector — adapted to the structured-source SPI (roadmap <strong>D22</strong>).
 *
 * <p>
 * Rather than a bespoke directory-walk that builds a loose attribute map per file, the Assets
 * connector is now a thin {@link DumStructuredSourcePlugin}: a {@link DumAssetsSourceProvider}
 * resolves each source as a {@link DumAssetsStructuredSource} (schema-as-code for the fixed field
 * set, directory-walk {@code discover()} + single-file {@code extract(id)}), and the shared loop
 * grounds, previews (D14), and tracks field coverage/drift (D15) and provenance (D16) for free —
 * wired to the same persistent stores the monitoring API reads. {@link #toJobItem} re-adds the
 * document {@code type} (content type) and {@code source_apps} the legacy path published.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component("assets")
public class DumAssetsPlugin extends DumStructuredSourcePlugin {

    /** Provider name reported for routing and monitoring. */
    public static final String PROVIDER_NAME = "ASSETS";

    /** The {@code source_apps} value stamped on every published document (legacy parity). */
    public static final String DOCUMENT_SOURCE_APPS = "dumont-assets";

    private final DumAssetsSourceProvider provider;

    public DumAssetsPlugin(DumConnectorContext dumConnectorContext, DumAssetsSourceProvider provider,
            DumPreviewSnapshotStore previewSnapshotStore,
            DumCoverageHistoryStore coverageHistoryStore,
            DumProvenanceHistoryStore provenanceHistoryStore) {
        super(dumConnectorContext, DumEnrichmentChain.empty(), previewSnapshotStore,
                coverageHistoryStore, provenanceHistoryStore);
        this.provider = provider;
    }

    @Override
    protected DumStructuredSourceProvider provider() {
        return provider;
    }

    @Override
    protected TurSNJobItem toJobItem(DumStructuredSource source, DumStructuredRecord structuredRecord) {
        TurSNJobItem item = super.toJobItem(source, structuredRecord);
        if (source instanceof DumAssetsStructuredSource assetsSource) {
            item.getAttributes().putIfAbsent(TurSNFieldName.TYPE, assetsSource.getContentType());
        }
        item.getAttributes().putIfAbsent(TurSNFieldName.SOURCE_APPS, DOCUMENT_SOURCE_APPS);
        return item;
    }
}
