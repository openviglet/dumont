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

package com.viglet.dumont.connector.plugin.db.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import com.viglet.dumont.connector.plugin.db.export.bean.DumDbExchange;
import com.viglet.dumont.connector.plugin.db.export.bean.DumDbSourceExchange;
import com.viglet.dumont.connector.plugin.db.persistence.model.DumDbField;
import com.viglet.dumont.connector.plugin.db.persistence.model.DumDbSource;
import com.viglet.dumont.connector.plugin.db.persistence.repository.DumDbSourceRepository;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class DumDbExchangeProcessTest {

    @Mock
    private DumDbSourceRepository dumDbSourceRepository;

    private DumDbExchangeProcess process;

    @BeforeEach
    void setUp() {
        process = new DumDbExchangeProcess(dumDbSourceRepository);
    }

    @Test
    void importFromFilePersistsFreshSourceWithFieldsAndSites(@TempDir Path tempDir)
            throws Exception {
        DumDbSourceExchange entry = fullSourceExchange().toBuilder().id(null).build();
        Path exportFile = tempDir.resolve("export.json");
        Files.writeString(exportFile,
                JsonMapper.builder().build().writeValueAsString(
                        DumDbExchange.builder().sources(List.of(entry)).build()),
                StandardCharsets.UTF_8);

        ArgumentCaptor<DumDbSource> saved = ArgumentCaptor.forClass(DumDbSource.class);

        process.importFromFile(exportFile.toFile());

        verify(dumDbSourceRepository).save(saved.capture());
        DumDbSource value = saved.getValue();
        assertThat(value.getName()).isEqualTo("People");
        assertThat(value.getDriver()).isEqualTo("org.h2.Driver");
        assertThat(value.getQuery()).isEqualTo("SELECT id, name FROM people");
        assertThat(value.getChunk()).isEqualTo(100);
        assertThat(value.isTypeInId()).isTrue();
        assertThat(value.getFields()).hasSize(1);
        assertThat(value.getTurSNSites()).containsExactly("People");
    }

    @Test
    void importSkipsSourceThatAlreadyExistsById() {
        DumDbSourceExchange existing =
                DumDbSourceExchange.builder().id("already").name("ignored").build();
        when(dumDbSourceRepository.findById("already"))
                .thenReturn(Optional.of(new DumDbSource()));

        process.importDbSources(DumDbExchange.builder().sources(List.of(existing)).build());

        verify(dumDbSourceRepository, never()).save(any());
    }

    @Test
    void importNullSourcesListIsSilentlySkipped(@TempDir Path tempDir) throws Exception {
        Path exportFile = tempDir.resolve("empty.json");
        Files.writeString(exportFile, "{}", StandardCharsets.UTF_8);

        process.importFromFile(exportFile.toFile());

        verify(dumDbSourceRepository, never()).save(any());
    }

    @Test
    void importMissingFileLogsErrorWithoutThrowing(@TempDir Path tempDir) {
        File missing = tempDir.resolve("does-not-exist.json").toFile();

        process.importFromFile(missing);

        verify(dumDbSourceRepository, never()).save(any());
    }

    @Test
    void exportObjectWritesZipAndStreamsBytes(@TempDir Path tempDir) throws Exception {
        String previousUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
        try {
            when(dumDbSourceRepository.findAll()).thenReturn(List.of(fullSourceExchangeSource()));
            HttpServletResponse response = org.mockito.Mockito.mock(HttpServletResponse.class);

            var body = process.exportObject(response);

            assertThat(body).isNotNull();
            verify(response).addHeader(org.mockito.ArgumentMatchers.eq("Content-disposition"),
                    org.mockito.ArgumentMatchers.contains(".zip"));
            verify(response).setContentType("application/octet-stream");
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
        when(dumDbSourceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        process.importFromMultipartFile(new InMemoryMultipartFile(buildZip(tempDir, false)));

        verify(dumDbSourceRepository).save(any(DumDbSource.class));
    }

    @Test
    void importFromMultipartFileImportsFolderWrappedZip(@TempDir Path tempDir) throws Exception {
        when(dumDbSourceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        process.importFromMultipartFile(new InMemoryMultipartFile(buildZip(tempDir, true)));

        verify(dumDbSourceRepository).save(any(DumDbSource.class));
    }

    /** Builds a ZIP holding export.json, either at the root or nested one folder deep. */
    private static byte[] buildZip(Path tempDir, boolean wrapped) throws Exception {
        DumDbSourceExchange entry = fullSourceExchange().toBuilder().id(null).build();
        String json = JsonMapper.builder().build()
                .writeValueAsString(DumDbExchange.builder().sources(List.of(entry)).build());
        File contentRoot = new File(tempDir.toFile(), "zip-" + (wrapped ? "w" : "f"));
        File jsonDir = wrapped ? new File(contentRoot, "inner") : contentRoot;
        Files.createDirectories(jsonDir.toPath());
        Files.writeString(new File(jsonDir, "export.json").toPath(), json);
        File zipFile = new File(tempDir.toFile(), "db-" + (wrapped ? "w" : "f") + ".zip");
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
            return "db.zip";
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

    private static DumDbSourceExchange fullSourceExchange() {
        return DumDbSourceExchange.builder()
                .id("test-id")
                .name("People")
                .description("People table")
                .driver("org.h2.Driver")
                .url("jdbc:h2:mem:test")
                .dbUsername("sa")
                .dbPassword("")
                .query("SELECT id, name FROM people")
                .site("Intranet")
                .locale("en_US")
                .contentType("People")
                .chunk(100)
                .typeInId(true)
                .multiValuedSeparator(",")
                .primaryKeyColumn("id")
                .fields(new ArrayList<>(List.of(DumDbField.builder().name("name").build())))
                .turSNSites(new HashSet<>(List.of("People")))
                .build();
    }

    private static DumDbSource fullSourceExchangeSource() {
        return DumDbSource.builder()
                .id("test-id")
                .name("People")
                .driver("org.h2.Driver")
                .url("jdbc:h2:mem:test")
                .query("SELECT id, name FROM people")
                .chunk(100)
                .fields(new ArrayList<>(List.of(DumDbField.builder().name("name").build())))
                .turSNSites(new HashSet<>(List.of("People")))
                .build();
    }
}
