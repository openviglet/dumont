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

package com.viglet.dumont.connector.plugin.assets.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

import com.viglet.dumont.connector.plugin.assets.export.bean.DumAssetsExchange;
import com.viglet.dumont.connector.plugin.assets.export.bean.DumAssetsSourceExchange;
import com.viglet.dumont.connector.plugin.assets.persistence.model.DumAssetsSource;
import com.viglet.dumont.connector.plugin.assets.persistence.repository.DumAssetsSourceRepository;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class DumAssetsExchangeProcessTest {

    @Mock
    private DumAssetsSourceRepository repository;

    private DumAssetsExchangeProcess process;

    @BeforeEach
    void setUp() {
        process = new DumAssetsExchangeProcess(repository);
    }

    @Test
    void importFromFilePersistsFreshSource(@TempDir Path tempDir) throws Exception {
        DumAssetsSourceExchange entry = fullExchange().toBuilder().id(null).build();
        Path exportFile = tempDir.resolve("export.json");
        Files.writeString(exportFile,
                JsonMapper.builder().build().writeValueAsString(
                        new DumAssetsExchange(List.of(entry))),
                StandardCharsets.UTF_8);

        ArgumentCaptor<DumAssetsSource> saved = ArgumentCaptor.forClass(DumAssetsSource.class);

        process.importFromFile(exportFile.toFile());

        verify(repository).save(saved.capture());
        DumAssetsSource value = saved.getValue();
        assertThat(value.getName()).isEqualTo("Docs");
        assertThat(value.getSourceDir()).isEqualTo("/data/docs");
        assertThat(value.getChunk()).isEqualTo(50);
        assertThat(value.getTurSNSites()).containsExactly("Docs");
    }

    @Test
    void importSkipsExistingSourceById() {
        DumAssetsSourceExchange existing =
                DumAssetsSourceExchange.builder().id("here").name("x").build();
        when(repository.findById("here")).thenReturn(Optional.of(new DumAssetsSource()));

        DumAssetsExchange exchange = new DumAssetsExchange(List.of(existing));
        // exercise via importFromFile by serializing
        // (importSource is private; the id guard is the branch under test)
        process.importFromFile(writeJson(exchange));

        verify(repository, never()).save(any());
    }

    @Test
    void importMissingFileLogsErrorWithoutThrowing(@TempDir Path tempDir) {
        process.importFromFile(tempDir.resolve("nope.json").toFile());

        verify(repository, never()).save(any());
    }

    @Test
    void exportObjectWritesZipAndStreamsBytes(@TempDir Path tempDir) throws Exception {
        String previousUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
        try {
            when(repository.findAll()).thenReturn(List.of(sourceEntity()));
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

    private File writeJson(DumAssetsExchange exchange) {
        try {
            Path f = Files.createTempFile("assets-export", ".json");
            Files.writeString(f, JsonMapper.builder().build().writeValueAsString(exchange),
                    StandardCharsets.UTF_8);
            f.toFile().deleteOnExit();
            return f.toFile();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static DumAssetsSourceExchange fullExchange() {
        return DumAssetsSourceExchange.builder()
                .id("a1").name("Docs").description("Documents")
                .sourceDir("/data/docs").prefixFromReplace("/data").prefixToReplace("/cdn")
                .site("Intranet").locale("en_US").contentType("Document").chunk(50)
                .typeInId(true).fileSizeField("size").fileExtensionField("ext")
                .encoding("UTF-8").showOutput(false)
                .turSNSites(new HashSet<>(List.of("Docs"))).build();
    }

    private static DumAssetsSource sourceEntity() {
        return DumAssetsSource.builder()
                .id("a1").name("Docs").sourceDir("/data/docs").site("Intranet")
                .locale("en_US").contentType("Document").chunk(50)
                .turSNSites(new HashSet<>(List.of("Docs"))).build();
    }
}
