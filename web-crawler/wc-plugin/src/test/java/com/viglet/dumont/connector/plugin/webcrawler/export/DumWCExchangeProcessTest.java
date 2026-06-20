/*
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

package com.viglet.dumont.connector.plugin.webcrawler.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
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

import org.springframework.web.multipart.MultipartFile;

import com.viglet.dumont.commons.utils.DumCommonsUtils;
import com.viglet.dumont.connector.plugin.webcrawler.export.bean.DumWCAttribExchange;
import com.viglet.dumont.connector.plugin.webcrawler.export.bean.DumWCExchange;
import com.viglet.dumont.connector.plugin.webcrawler.export.bean.DumWCSourceExchange;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCAllowUrl;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCAttributeMapping;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCFileExtension;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCNotAllowUrl;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCSource;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCStartingPoint;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCAllowUrlRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCAttributeMappingRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCFileExtensionRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCNotAllowUrlRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCSourceRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCStartingPointRepository;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class DumWCExchangeProcessTest {

    @Mock
    private DumWCSourceRepository dumWCSourceRepository;
    @Mock
    private DumWCAllowUrlRepository dumWCAllowUrlRepository;
    @Mock
    private DumWCStartingPointRepository dumWCStartingPointRepository;
    @Mock
    private DumWCNotAllowUrlRepository dumWCNotAllowUrlRepository;
    @Mock
    private DumWCFileExtensionRepository dumWCFileExtensionRepository;
    @Mock
    private DumWCAttributeMappingRepository dumWCAttributeMappingRepository;

    private DumWCExchangeProcess process;

    @BeforeEach
    void setUp() {
        process = new DumWCExchangeProcess(dumWCSourceRepository, dumWCAllowUrlRepository,
                dumWCStartingPointRepository, dumWCNotAllowUrlRepository,
                dumWCFileExtensionRepository, dumWCAttributeMappingRepository);
    }

    @Test
    void importFromFilePersistsSourceAndAllChildCollections(@TempDir Path tempDir)
            throws Exception {
        DumWCSourceExchange entry = DumWCSourceExchange.builder()
                .title("Wiki").description("Public wiki").url("https://wiki.invalid")
                .locale(Locale.ENGLISH).username("u").password("p").enableSitemap(true)
                .crawlDelay(1000).fetcher("jsoup").userAgentStrategy("PER_SOURCE").workers(4)
                .turSNSites(new HashSet<>(List.of("Wiki")))
                .startingPoints(new HashSet<>(List.of("https://wiki.invalid/start")))
                .allowUrls(new HashSet<>(List.of("https://wiki.invalid/allow")))
                .notAllowUrls(new HashSet<>(List.of("https://wiki.invalid/deny")))
                .notAllowExtensions(new HashSet<>(List.of("pdf")))
                .attributes(new HashSet<>(List.of(
                        DumWCAttribExchange.builder().name("title").className("c").text("t").build())))
                .build();
        Path exportFile = tempDir.resolve("export.json");
        Files.writeString(exportFile,
                JsonMapper.builder().build().writeValueAsString(new DumWCExchange(List.of(entry))),
                StandardCharsets.UTF_8);

        ArgumentCaptor<DumWCSource> saved = ArgumentCaptor.forClass(DumWCSource.class);

        process.importFromFile(exportFile.toFile());

        verify(dumWCSourceRepository).save(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("Wiki");
        verify(dumWCStartingPointRepository, times(1)).save(any(DumWCStartingPoint.class));
        verify(dumWCAllowUrlRepository, times(1)).save(any(DumWCAllowUrl.class));
        verify(dumWCNotAllowUrlRepository, times(1)).save(any(DumWCNotAllowUrl.class));
        verify(dumWCFileExtensionRepository, times(1)).save(any(DumWCFileExtension.class));
        verify(dumWCAttributeMappingRepository, times(1)).save(any(DumWCAttributeMapping.class));
    }

    @Test
    void importSkipsExistingSourceById() {
        DumWCSourceExchange existing = DumWCSourceExchange.builder().id("here").title("x").build();
        when(dumWCSourceRepository.findById("here")).thenReturn(Optional.of(new DumWCSource()));

        process.importWCSource(new DumWCExchange(List.of(existing)));

        verify(dumWCSourceRepository, never()).save(any());
    }

    @Test
    void importFromFileWithEmptySourcesIsNoOp(@TempDir Path tempDir) throws Exception {
        Path exportFile = tempDir.resolve("export.json");
        Files.writeString(exportFile,
                JsonMapper.builder().build().writeValueAsString(new DumWCExchange(List.of())),
                StandardCharsets.UTF_8);

        process.importFromFile(exportFile.toFile());

        verify(dumWCSourceRepository, never()).save(any());
    }

    @Test
    void exportObjectWritesZipAndStreamsBytes(@TempDir Path tempDir) throws Exception {
        String previousUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
        try {
            DumWCSource source = DumWCSource.builder()
                    .id("s1").title("Wiki").url("https://wiki.invalid")
                    .turSNSites(new HashSet<>(List.of("Wiki"))).build();
            when(dumWCSourceRepository.findAll()).thenReturn(List.of(source));
            HttpServletResponse response = mock(HttpServletResponse.class);

            var body = process.exportObject(response);

            assertThat(body).isNotNull();
            verify(response).addHeader(eq("Content-disposition"), contains(".zip"));
            verify(response).setStatus(HttpServletResponse.SC_OK);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            body.writeTo(out);
            assertThat(out.size()).isPositive();
        } finally {
            System.setProperty("user.dir", previousUserDir);
        }
    }

    @Test
    void importFromMultipartFileImportsFlatZip(@TempDir Path tempDir) throws Exception {
        when(dumWCSourceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        process.importFromMultipartFile(new InMemoryMultipartFile(buildZip(tempDir, false)));

        verify(dumWCSourceRepository).save(any(DumWCSource.class));
    }

    @Test
    void importFromMultipartFileImportsFolderWrappedZip(@TempDir Path tempDir) throws Exception {
        when(dumWCSourceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        process.importFromMultipartFile(new InMemoryMultipartFile(buildZip(tempDir, true)));

        verify(dumWCSourceRepository).save(any(DumWCSource.class));
    }

    /** Builds a ZIP holding export.json, either at the root or nested one folder deep. */
    private static byte[] buildZip(Path tempDir, boolean wrapped) throws Exception {
        DumWCSourceExchange entry = DumWCSourceExchange.builder()
                .title("Wiki").url("https://wiki.invalid")
                .turSNSites(new HashSet<>()).startingPoints(new HashSet<>())
                .allowUrls(new HashSet<>()).notAllowUrls(new HashSet<>())
                .notAllowExtensions(new HashSet<>()).attributes(new HashSet<>()).build();
        String json =
                JsonMapper.builder().build().writeValueAsString(new DumWCExchange(List.of(entry)));
        File contentRoot = new File(tempDir.toFile(), "zip-" + (wrapped ? "w" : "f"));
        File jsonDir = wrapped ? new File(contentRoot, "inner") : contentRoot;
        Files.createDirectories(jsonDir.toPath());
        Files.writeString(new File(jsonDir, "export.json").toPath(), json);
        File zipFile = new File(tempDir.toFile(), "wc-" + (wrapped ? "w" : "f") + ".zip");
        DumCommonsUtils.addFilesToZip(contentRoot, zipFile);
        return Files.readAllBytes(zipFile.toPath());
    }

    /** Minimal {@link MultipartFile} over an in-memory byte[] — avoids a spring-test dependency. */
    private record InMemoryMultipartFile(byte[] bytes) implements MultipartFile {
        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return "wc.zip";
        }

        @Override
        public String getContentType() {
            return "application/zip";
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(File dest) throws java.io.IOException {
            Files.write(dest.toPath(), bytes);
        }
    }
}
