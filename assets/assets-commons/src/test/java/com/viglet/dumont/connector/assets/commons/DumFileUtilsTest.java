/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.viglet.dumont.connector.assets.commons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.viglet.dumont.commons.file.DumFileAttributes;

class DumFileUtilsTest {

    private static final String TEXT = "Dumont data exchange platform sample content.";

    private File writeText(Path dir, String name) throws Exception {
        File file = dir.resolve(name).toFile();
        Files.writeString(file.toPath(), TEXT, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void readFileReturnsNullWhenMissing() {
        assertThat(DumFileUtils.readFile("does-not-exist-" + System.nanoTime() + ".txt")).isNull();
    }

    @Test
    void readFileParsesExistingTextFile(@TempDir Path dir) throws Exception {
        DumTikaFileAttributes attrs = DumFileUtils.readFile(writeText(dir, "sample.txt"));

        assertThat(attrs).isNotNull();
        assertThat(attrs.getContent()).contains("Dumont");
        assertThat(attrs.getMetadata()).isNotNull();
    }

    @Test
    void parseFileFromMultipartExtractsContent() {
        MockMultipartFile multipart = new MockMultipartFile("file", "doc.txt", "text/plain",
                TEXT.getBytes(StandardCharsets.UTF_8));

        DumTikaFileAttributes attrs = DumFileUtils.parseFile(multipart);

        assertThat(attrs).isNotNull();
        assertThat(attrs.getContent()).contains("platform");
    }

    @Test
    void documentToTextMapsMultipartAttributes() {
        MockMultipartFile multipart = new MockMultipartFile("file", "report.txt", "text/plain",
                TEXT.getBytes(StandardCharsets.UTF_8));

        DumFileAttributes attrs = DumFileUtils.documentToText(multipart);

        assertThat(attrs.getName()).isEqualTo("report.txt");
        assertThat(attrs.getExtension()).isEqualTo("txt");
        assertThat(attrs.getContent()).contains("Dumont");
        assertThat(attrs.getLastModified()).isNotNull();
        assertThat(attrs.getMetadata()).isNotEmpty();
    }

    @Test
    void parseDocumentReadsStreamContent() throws Exception {
        Optional<String> content =
                DumFileUtils.parseDocument(new ByteArrayInputStream(TEXT.getBytes(StandardCharsets.UTF_8)));

        assertThat(content).isPresent();
        assertThat(content.get()).contains("exchange");
    }

    @Test
    void urlContentToTextBlocksNonHttpProtocol(@TempDir Path dir) throws Exception {
        URL fileUrl = writeText(dir, "blocked.txt").toURI().toURL();

        DumFileAttributes attrs = DumFileUtils.urlContentToText(fileUrl);

        // file:// is not http/https -> SSRF guard blocks it -> empty attributes.
        assertThat(attrs.getContent()).isNull();
    }

    @Test
    void urlContentToTextBlocksLoopbackHost() throws Exception {
        DumFileAttributes attrs = DumFileUtils.urlContentToText(new URL("http://127.0.0.1/secret"));

        assertThat(attrs.getContent()).isNull();
    }

    @Test
    void constructorIsNotInstantiable() throws Exception {
        Constructor<DumFileUtils> ctor = DumFileUtils.class.getDeclaredConstructor();
        ctor.setAccessible(true);

        assertThatThrownBy(ctor::newInstance).isInstanceOf(InvocationTargetException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }
}
