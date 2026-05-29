/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.plugin.eds.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.plugin.eds.export.bean.DumEDSExchange;
import com.viglet.dumont.connector.plugin.eds.export.bean.DumEDSFacetExchange;
import com.viglet.dumont.connector.plugin.eds.export.bean.DumEDSFieldMappingExchange;
import com.viglet.dumont.connector.plugin.eds.export.bean.DumEDSSourceExchange;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSExcludePattern;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSFacet;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSFieldMapping;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSIndex;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSRemoveSelector;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSExcludePatternRepository;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSFacetRepository;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSFieldMappingRepository;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSIndexRepository;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSRemoveSelectorRepository;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSSourceRepository;

@ExtendWith(MockitoExtension.class)
class DumEDSExchangeProcessTest {

    @Mock
    private DumEDSSourceRepository sourceRepository;
    @Mock
    private DumEDSIndexRepository indexRepository;
    @Mock
    private DumEDSExcludePatternRepository excludePatternRepository;
    @Mock
    private DumEDSRemoveSelectorRepository removeSelectorRepository;
    @Mock
    private DumEDSFacetRepository facetRepository;
    @Mock
    private DumEDSFieldMappingRepository fieldMappingRepository;

    private DumEDSExchangeProcess process;

    @BeforeEach
    void setUp() {
        process = new DumEDSExchangeProcess(sourceRepository, indexRepository,
                excludePatternRepository, removeSelectorRepository, facetRepository,
                fieldMappingRepository);
    }

    @Test
    void toExchangeFlattensCollectionsAndKeepsStructuredOnes() {
        DumEDSSource source = fullSource();

        DumEDSSourceExchange exchange = process.toExchange(source);

        assertThat(exchange.getId()).isEqualTo("test-id");
        assertThat(exchange.getTitle()).isEqualTo("Example");
        assertThat(exchange.getBaseUrl()).isEqualTo("https://example.invalid");
        assertThat(exchange.getFacetPathPattern())
                .isEqualTo("/{locale}/{audience}/{section}/**");
        assertThat(exchange.getIndexes()).containsExactly("/query-index.json");
        assertThat(exchange.getExcludePatterns()).containsExactly("/footer");
        assertThat(exchange.getRemoveSelectors()).containsExactly("nav", "footer");
        assertThat(exchange.getFacets()).hasSize(2);
        assertThat(exchange.getFacets()).extracting(DumEDSFacetExchange::getName)
                .containsExactly("locale", "audience");
        assertThat(exchange.getFieldMappings()).singleElement()
                .satisfies(m -> {
                    assertThat(m.getSourceField()).isEqualTo("title");
                    assertThat(m.getTargetField()).isEqualTo("page_title");
                });
    }

    @Test
    void roundTripJsonPreservesAllFields() {
        DumEDSExchange original = DumEDSExchange.builder()
                .sources(List.of(process.toExchange(fullSource())))
                .build();

        String json = DumEDSExchangeProcess.toJson(original);
        DumEDSExchange parsed = DumEDSExchangeProcess.fromJson(json);

        assertThat(parsed.getSources()).hasSize(1);
        DumEDSSourceExchange roundTripped = parsed.getSources().iterator().next();
        assertThat(roundTripped.getTitle()).isEqualTo("Example");
        assertThat(roundTripped.getIndexes()).containsExactly("/query-index.json");
        assertThat(roundTripped.getFacets()).hasSize(2);
        assertThat(roundTripped.getFieldMappings()).singleElement()
                .extracting(DumEDSFieldMappingExchange::getTargetField).isEqualTo("page_title");
    }

    @Test
    void importFromFilePersistsSourceAndAllChildCollections(@TempDir Path tempDir)
            throws Exception {
        DumEDSExchange exchange = DumEDSExchange.builder()
                .sources(List.of(process.toExchange(fullSource()).toBuilder()
                        .id(null)  // simulate fresh import
                        .build()))
                .build();
        Path exportFile = tempDir.resolve("export.json");
        Files.writeString(exportFile, DumEDSExchangeProcess.toJson(exchange),
                StandardCharsets.UTF_8);

        // id=null on the exchange row short-circuits the findById guard,
        // so persistSource runs without a Mockito stub for the lookup.
        ArgumentCaptor<DumEDSSource> savedSource = ArgumentCaptor.forClass(DumEDSSource.class);

        process.importFromFile(exportFile.toFile());

        verify(sourceRepository).save(savedSource.capture());
        assertThat(savedSource.getValue().getTitle()).isEqualTo("Example");
        assertThat(savedSource.getValue().getBaseUrl()).isEqualTo("https://example.invalid");

        verify(indexRepository, times(1)).save(any(DumEDSIndex.class));
        verify(excludePatternRepository, times(1)).save(any(DumEDSExcludePattern.class));
        verify(removeSelectorRepository, times(2)).save(any(DumEDSRemoveSelector.class));
        verify(facetRepository, times(2)).save(any(DumEDSFacet.class));
        verify(fieldMappingRepository, times(1)).save(any(DumEDSFieldMapping.class));
    }

    @Test
    void importSkipsExistingSourceById() {
        // When a source's id is already present, import is a no-op for that entry.
        DumEDSSourceExchange existing = DumEDSSourceExchange.builder()
                .id("already-exists")
                .title("ignored")
                .baseUrl("https://x.invalid")
                .build();
        when(sourceRepository.findById("already-exists"))
                .thenReturn(Optional.of(new DumEDSSource()));

        process.importSources(DumEDSExchange.builder().sources(List.of(existing)).build());

        verify(sourceRepository, times(0)).save(any());
    }

    @Test
    void emptyExportsListIsSilentlySkipped(@TempDir Path tempDir) throws Exception {
        DumEDSExchange exchange = DumEDSExchange.builder().sources(List.of()).build();
        Path exportFile = tempDir.resolve("export.json");
        Files.writeString(exportFile, DumEDSExchangeProcess.toJson(exchange),
                StandardCharsets.UTF_8);

        process.importFromFile(exportFile.toFile());

        verify(sourceRepository, times(0)).save(any());
    }

    private static DumEDSSource fullSource() {
        DumEDSSource source = new DumEDSSource();
        source.setId("test-id");
        source.setTitle("Example");
        source.setDescription("Example PT-BR via EDS");
        source.setLocale(Locale.forLanguageTag("pt-BR"));
        source.setBaseUrl("https://example.invalid");
        source.setSitemapFallback("/sitemap.xml");
        source.setBodySelector("main");
        source.setUserAgent("Viglet-Dumont-EDS/1.0");
        source.setFetchTimeoutMs(10_000);
        source.setRequestsPerSecond(50);
        source.setMaxConcurrent(8);
        source.setFacetPathPattern("/{locale}/{audience}/{section}/**");
        source.getTurSNSites().add("example-pt-br");
        source.getIndexes().add(new DumEDSIndex("/query-index.json", source));
        source.getExcludePatterns().add(new DumEDSExcludePattern("/footer", source));
        source.getRemoveSelectors().add(new DumEDSRemoveSelector("nav", source));
        source.getRemoveSelectors().add(new DumEDSRemoveSelector("footer", source));
        source.getFacets().add(new DumEDSFacet("locale", 0, null, source));
        source.getFacets().add(new DumEDSFacet("audience", 1, "pf,pj", source));
        source.getFieldMappings().add(new DumEDSFieldMapping("title", "page_title", source));
        return source;
    }

    @Test
    void importingFileWithoutFile(@TempDir Path tempDir) {
        File missing = tempDir.resolve("does-not-exist.json").toFile();

        assertThatExceptionRaised(() -> process.importFromFile(missing));
    }

    private static void assertThatExceptionRaised(Runnable r) {
        try {
            r.run();
            throw new AssertionError("Expected exception");
        } catch (Exception expected) {
            // Pass — jackson surfaces a FileNotFoundException wrapped as JacksonException.
            assertThat(expected).isNotNull();
        }
    }
}
