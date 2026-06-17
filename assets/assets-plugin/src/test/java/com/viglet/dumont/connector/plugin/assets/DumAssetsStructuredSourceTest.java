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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageSnapshot;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumInMemoryCoverageHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumInMemoryPreviewSnapshotStore;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumStructuredPreview;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumInMemoryProvenanceHistoryStore;
import com.viglet.dumont.connector.plugin.assets.persistence.model.DumAssetsSource;
import com.viglet.dumont.connector.plugin.assets.persistence.repository.DumAssetsSourceRepository;
import com.viglet.dumont.connector.plugin.assets.source.DumAssetsSourceProvider;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.sn.field.TurSNFieldName;

/**
 * End-to-end Assets adapter (roadmap <strong>D22</strong>): a filesystem source indexes through the
 * structured plugin — directory-walk {@code discover()} + single-file {@code extract(id)} — with
 * grounding, coverage history and dry-run preview all working off the shared loop, and the fixed
 * field set declared as schema-as-code.
 */
class DumAssetsStructuredSourceTest {

    private static final String SOURCE = "files";

    @TempDir
    Path tempDir;

    private Path textFile;
    private Path imageFile;

    /** Captures every job item the plugin publishes. */
    private static final class CapturingContext implements DumConnectorContext {
        final List<TurSNJobItem> published = new ArrayList<>();

        @Override
        public boolean addJobItem(DumJobItemWithSession item) {
            published.add(item.turSNJobItem());
            return true;
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            // no-op
        }

        @Override
        public List<DumConnectorIndexing> getIndexingItem(String objectId, String source,
                String provider) {
            return List.of();
        }

        @Override
        public List<String> getObjectIdByDependency(String source, String provider,
                List<String> dependenciesObjectIdList) {
            return List.of();
        }
    }

    @BeforeEach
    void seed() throws IOException {
        textFile = Files.writeString(tempDir.resolve("doc.txt"), "hello world from dumont");
        imageFile = Files.createFile(tempDir.resolve("photo.png"));
        // A macOS metadata file that the walk must skip.
        Files.createFile(tempDir.resolve(".DS_Store"));
    }

    private DumAssetsSource source() {
        return DumAssetsSource.builder()
                .name(SOURCE)
                .sourceDir(tempDir.toString())
                .locale("en_US")
                .contentType("Static File")
                .chunk(100)
                .fileSizeField("fileSize")
                .fileExtensionField("fileExtension")
                .contentField("content")
                .turSNSites(Set.of("Sample"))
                .build();
    }

    private DumAssetsSourceProvider provider(DumAssetsSource source) {
        DumAssetsSourceRepository repository = mock(DumAssetsSourceRepository.class);
        when(repository.findByName(SOURCE)).thenReturn(Optional.of(source));
        when(repository.findAll()).thenReturn(List.of(source));
        return new DumAssetsSourceProvider(repository);
    }

    private DumAssetsPlugin plugin(CapturingContext context, DumAssetsSourceProvider provider,
            DumInMemoryCoverageHistoryStore coverageStore) {
        return new DumAssetsPlugin(context, provider, new DumInMemoryPreviewSnapshotStore(),
                coverageStore, new DumInMemoryProvenanceHistoryStore());
    }

    @Test
    void discoverWalksFilesAndSkipsDsStore() {
        var structuredSource = provider(source()).resolve(SOURCE).orElseThrow();

        assertThat(structuredSource.discover()).containsExactlyInAnyOrder(
                textFile.toFile().getAbsolutePath(), imageFile.toFile().getAbsolutePath());
    }

    @Test
    void extractResolvesASingleFileIntoATypedRecord() {
        var structuredSource = provider(source()).resolve(SOURCE).orElseThrow();

        var record = structuredSource.extract(textFile.toFile().getAbsolutePath()).orElseThrow();
        assertThat(record.values()).containsEntry(TurSNFieldName.TITLE, "doc.txt");
        assertThat(record.values()).containsEntry("fileExtension", "txt");
        assertThat(record.values().get(TurSNFieldName.URL).toString()).endsWith("doc.txt");

        assertThat(structuredSource.extract(tempDir.resolve("missing.txt").toString())).isEmpty();
    }

    @Test
    void indexesThroughTheStructuredLoopStampingTypeAndSourceApps() {
        DumAssetsSource source = source();
        CapturingContext context = new CapturingContext();
        DumAssetsPlugin plugin =
                plugin(context, provider(source), new DumInMemoryCoverageHistoryStore());

        plugin.indexAll(SOURCE);

        assertThat(context.published).hasSize(2);
        TurSNJobItem text = context.published.stream()
                .filter(item -> "doc.txt".equals(item.getAttributes().get(TurSNFieldName.TITLE)))
                .findFirst().orElseThrow();
        // Legacy parity: content type + source_apps stamped on every document.
        assertThat(text.getAttributes()).containsEntry(TurSNFieldName.TYPE, "Static File");
        assertThat(text.getAttributes()).containsEntry(TurSNFieldName.SOURCE_APPS,
                DumAssetsPlugin.DOCUMENT_SOURCE_APPS);
        // Tika content extracted into the configured field.
        assertThat(text.getAttributes().get("content").toString()).contains("hello world");
        // The text file is not an image → grounding omits the image field.
        assertThat(text.getAttributes()).doesNotContainKey(TurSNFieldName.IMAGE);

        TurSNJobItem image = context.published.stream()
                .filter(item -> "photo.png".equals(item.getAttributes().get(TurSNFieldName.TITLE)))
                .findFirst().orElseThrow();
        assertThat(image.getAttributes()).containsKey(TurSNFieldName.IMAGE);
    }

    @Test
    void recordsCoverageHistoryForTheRun() {
        DumInMemoryCoverageHistoryStore coverageStore = new DumInMemoryCoverageHistoryStore();
        DumAssetsPlugin plugin = plugin(new CapturingContext(), provider(source()), coverageStore);

        plugin.indexAll(SOURCE);

        List<DumCoverageSnapshot> history = coverageStore.history(SOURCE);
        assertThat(history).hasSize(1);
        DumCoverageSnapshot run = history.getFirst();
        assertThat(run.documentCount()).isEqualTo(2);
        assertThat(run.field(TurSNFieldName.TITLE).orElseThrow().percent()).isEqualTo(100);
        assertThat(run.field(TurSNFieldName.URL).orElseThrow().percent()).isEqualTo(100);
    }

    @Test
    void previewSamplesWithoutWriting() {
        CapturingContext context = new CapturingContext();
        DumAssetsPlugin plugin =
                plugin(context, provider(source()), new DumInMemoryCoverageHistoryStore());

        DumStructuredPreview preview = plugin.preview(SOURCE, 10);

        assertThat(preview).isNotNull();
        assertThat(preview.discovered()).isEqualTo(2);
        assertThat(preview.sampled()).isEqualTo(2);
        assertThat(context.published).isEmpty();
    }
}
