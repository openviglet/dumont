/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.spi.structured.DumEmbeddingRole;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageSnapshot;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumInMemoryCoverageHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumInMemoryPreviewSnapshotStore;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumStructuredPreview;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumInMemoryProvenanceHistoryStore;
import com.viglet.dumont.connector.plugin.db.persistence.model.DumDbField;
import com.viglet.dumont.connector.plugin.db.persistence.model.DumDbSource;
import com.viglet.dumont.connector.plugin.db.persistence.repository.DumDbSourceRepository;
import com.viglet.dumont.connector.plugin.db.source.DumDbRowMapper;
import com.viglet.dumont.connector.plugin.db.source.DumDbStructuredSource;
import com.viglet.dumont.connector.plugin.db.source.DumDbSourceProvider;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.field.TurSNFieldName;

/**
 * End-to-end DB pilot (roadmap <strong>D20</strong>): an H2-backed source indexes through the
 * structured plugin — id-list {@code discover()} + single-row {@code extract(id)} — with grounding,
 * coverage history and dry-run preview all working off the shared loop.
 */
class DumDbStructuredSourceTest {

    private static final String URL = "jdbc:h2:mem:dumont_db_d20;DB_CLOSE_DELAY=-1";
    private static final String SOURCE = "article";

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
    void seed() throws SQLException {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS article");
            stmt.execute("CREATE TABLE article (id INT PRIMARY KEY, title VARCHAR(255), "
                    + "body VARCHAR(255), tags VARCHAR(255), secret VARCHAR(255))");
            stmt.execute("INSERT INTO article VALUES (1, 'Algorithms', 'About algorithms', "
                    + "'cs,math', 'hidden')");
            stmt.execute("INSERT INTO article VALUES (2, 'Databases', NULL, 'db', 'hidden')");
            stmt.execute("INSERT INTO article VALUES (3, NULL, 'No title here', 'misc', 'hidden')");
        }
    }

    @AfterEach
    void drop() throws SQLException {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS article");
        }
    }

    private DumDbSource source() {
        DumDbSource source = DumDbSource.builder()
                .name(SOURCE)
                .driver("org.h2.Driver")
                .url(URL)
                .dbUsername("sa")
                .dbPassword("")
                .query("SELECT id AS id, title AS title, body AS body, tags AS tags FROM article")
                .primaryKeyColumn("id")
                .contentType("Article")
                .locale("en_US")
                .multiValuedSeparator(",")
                .turSNSites(Set.of("Sample"))
                .build();
        source.getFields().add(DumDbField.builder().name("title").type(TurSEFieldType.TEXT)
                .mandatory(true).embeddingRole(DumEmbeddingRole.TITLE).build());
        source.getFields().add(DumDbField.builder().name("body").type(TurSEFieldType.TEXT)
                .embeddingRole(DumEmbeddingRole.BODY).build());
        source.getFields().add(DumDbField.builder().name("tags").type(TurSEFieldType.STRING)
                .multiValued(true).facet(true).build());
        return source;
    }

    private DumDbSourceProvider provider(DumDbSource source) {
        DumDbSourceRepository repository = mock(DumDbSourceRepository.class);
        when(repository.findByName(SOURCE)).thenReturn(Optional.of(source));
        when(repository.findAll()).thenReturn(List.of(source));
        return new DumDbSourceProvider(repository, new DumDbRowMapper());
    }

    private DumDbPlugin plugin(CapturingContext context, DumDbSourceProvider provider,
            DumInMemoryCoverageHistoryStore coverageStore) {
        return new DumDbPlugin(context, provider, new DumInMemoryPreviewSnapshotStore(),
                coverageStore, new DumInMemoryProvenanceHistoryStore());
    }

    @Test
    void indexesThroughTheStructuredLoopGroundingAndSkippingInvalidRows() {
        DumDbSource source = source();
        CapturingContext context = new CapturingContext();
        DumDbPlugin plugin = plugin(context, provider(source), new DumInMemoryCoverageHistoryStore());

        plugin.indexAll(SOURCE);

        // Rows 1 and 2 carry the mandatory title; row 3 has none → skipped.
        assertThat(context.published).extracting(TurSNJobItem::getId).containsExactly("1", "2");

        TurSNJobItem first = context.published.getFirst();
        assertThat(first.getAttributes()).containsEntry("title", "Algorithms");
        // Multi-valued split on the configured separator.
        assertThat(first.getAttributes()).containsEntry("tags", List.of("cs", "math"));
        // Legacy parity: content type + source_apps stamped on every document.
        assertThat(first.getAttributes()).containsEntry(TurSNFieldName.TYPE, "Article");
        assertThat(first.getAttributes()).containsEntry(TurSNFieldName.SOURCE_APPS,
                DumDbPlugin.DOCUMENT_SOURCE_APPS);
        // The query never selected "secret" → it is not indexed.
        assertThat(first.getAttributes()).doesNotContainKey("secret");

        // Grounding survives end to end: row 2 supplied no body, so the key is absent.
        assertThat(context.published.get(1).getAttributes()).doesNotContainKey("body");
    }

    @Test
    void discoverListsIdsAndExtractFetchesASingleRow() {
        DumDbSource source = source();
        var structuredSource = provider(source).resolve(SOURCE).orElseThrow();

        assertThat(structuredSource.discover()).containsExactlyInAnyOrder("1", "2", "3");
        assertThat(structuredSource.extract("1")).isPresent();
        assertThat(structuredSource.extract("1").orElseThrow().values()).containsEntry("title",
                "Algorithms");
        assertThat(structuredSource.extract("999")).isEmpty();
    }

    @Test
    void recordsCoverageHistoryForTheRun() {
        DumDbSource source = source();
        DumInMemoryCoverageHistoryStore coverageStore = new DumInMemoryCoverageHistoryStore();
        DumDbPlugin plugin = plugin(new CapturingContext(), provider(source), coverageStore);

        plugin.indexAll(SOURCE);

        List<DumCoverageSnapshot> history = coverageStore.history(SOURCE);
        assertThat(history).hasSize(1);
        DumCoverageSnapshot run = history.getFirst();
        assertThat(run.documentCount()).isEqualTo(2);
        assertThat(run.field("title").orElseThrow().percent()).isEqualTo(100);
        assertThat(run.field("body").orElseThrow().percent()).isEqualTo(50);
    }

    @Test
    void previewSamplesWithoutWriting() {
        DumDbSource source = source();
        CapturingContext context = new CapturingContext();
        DumDbPlugin plugin = plugin(context, provider(source), new DumInMemoryCoverageHistoryStore());

        DumStructuredPreview preview = plugin.preview(SOURCE, 10);

        assertThat(preview).isNotNull();
        assertThat(preview.discovered()).isEqualTo(3);
        assertThat(preview.sampled()).isEqualTo(3);
        // Row 3 (missing mandatory title) is reported as a would-be skip.
        assertThat(preview.skipped()).extracting(DumStructuredPreview.SkippedRecord::id)
                .contains("3");
        // A dry run publishes nothing.
        assertThat(context.published).isEmpty();
    }

    @Test
    void derivesSchemaFromQueryWhenNoFieldsDeclared() {
        DumDbSource source = source();
        source.getFields().clear();
        CapturingContext context = new CapturingContext();
        DumDbPlugin plugin = plugin(context, provider(source), new DumInMemoryCoverageHistoryStore());

        plugin.indexAll(SOURCE);

        // No mandatory fields when derived → all three rows publish, every column indexed as text.
        assertThat(context.published).extracting(TurSNJobItem::getId).containsExactly("1", "2", "3");
        // Derived field names are the DB's own column labels (legacy parity), case as the DB returns.
        assertThat(context.published.getFirst().getAttributes()).containsValue("Algorithms");
    }

    @Test
    void discoverAndExtractReturnEmptyOnSqlError() {
        // A valid H2 connection but a query against a missing table: executeQuery throws
        // SQLException, which both discover() and extract() swallow into an empty result.
        DumDbSource broken = source().toBuilder()
                .query("SELECT id FROM table_that_does_not_exist").build();
        DumDbStructuredSource src = new DumDbStructuredSource(broken, new DumDbRowMapper());

        assertThat(src.discover()).isEmpty();
        assertThat(src.extract("1")).isEmpty();
    }
}
