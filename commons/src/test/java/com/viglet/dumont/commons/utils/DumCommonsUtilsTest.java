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

package com.viglet.dumont.commons.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.commons.collections4.KeyValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for DumCommonsUtils.
 *
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
class DumCommonsUtilsTest {

    @Test
    void testGetKeyValueFromColonValid() {
        String input = "key:value";
        Optional<KeyValue<String, String>> result = DumCommonsUtils.getKeyValueFromColon(input);

        assertThat(result).isPresent();
        assertThat(result.get().getKey()).isEqualTo("key");
        assertThat(result.get().getValue()).isEqualTo("value");
    }

    @Test
    void testGetKeyValueFromColonWithMultipleColons() {
        String input = "key:value:extra";
        Optional<KeyValue<String, String>> result = DumCommonsUtils.getKeyValueFromColon(input);

        assertThat(result).isPresent();
        assertThat(result.get().getKey()).isEqualTo("key");
        assertThat(result.get().getValue()).isEqualTo("value:extra");
    }

    @Test
    void testGetKeyValueFromColonNoColon() {
        String input = "keyvalue";
        Optional<KeyValue<String, String>> result = DumCommonsUtils.getKeyValueFromColon(input);

        assertThat(result).isEmpty();
    }

    @Test
    void testGetKeyValueFromColonEmptyValue() {
        String input = "key:";
        Optional<KeyValue<String, String>> result = DumCommonsUtils.getKeyValueFromColon(input);

        // Based on the actual implementation, this returns empty when there's only one
        // part after split
        assertThat(result).isEmpty();
    }

    @Test
    void testHtml2Text() {
        String html = "<p>This is a <strong>test</strong> with <em>HTML</em> tags.</p>";
        String result = DumCommonsUtils.html2Text(html);

        assertThat(result).isEqualTo("This is a test with HTML tags.");
    }

    @Test
    void testHtml2TextWithComplexHtml() {
        String html = "<div><h1>Title</h1><p>Paragraph with <a href=\"#\">link</a></p></div>";
        String result = DumCommonsUtils.html2Text(html);

        assertThat(result).isEqualTo("Title Paragraph with link");
    }

    @Test
    void testAddFilesToZip(@TempDir Path tempDir) throws IOException {
        // Create source directory with test files
        File sourceDir = tempDir.resolve("source").toFile();
        assertThat(sourceDir.mkdirs()).isTrue();

        File testFile1 = new File(sourceDir, "test1.txt");
        Files.write(testFile1.toPath(), "Test content 1".getBytes());

        File testFile2 = new File(sourceDir, "test2.txt");
        Files.write(testFile2.toPath(), "Test content 2".getBytes());

        // Create destination zip
        File destinationZip = tempDir.resolve("test.zip").toFile();

        // Test the method
        DumCommonsUtils.addFilesToZip(sourceDir, destinationZip);

        // Verify zip was created
        assertThat(destinationZip).exists();
        assertThat(destinationZip.length()).isGreaterThan(0);
    }

    @Test
    void testUnZipIt(@TempDir Path tempDir) throws IOException {
        // Create a test file structure
        File sourceDir = tempDir.resolve("source").toFile();
        assertThat(sourceDir.mkdirs()).isTrue();

        File testFile = new File(sourceDir, "test.txt");
        Files.write(testFile.toPath(), "Test content".getBytes());

        // Create zip
        File zipFile = tempDir.resolve("test.zip").toFile();
        DumCommonsUtils.addFilesToZip(sourceDir, zipFile);

        // Create extraction directory
        File extractDir = tempDir.resolve("extract").toFile();
        assertThat(extractDir.mkdirs()).isTrue();

        // Test unzipping
        DumCommonsUtils.unZipIt(zipFile, extractDir);

        // Verify extracted files
        File extractedFile = new File(extractDir, "test.txt");
        assertThat(extractedFile).exists();
        assertThat(Files.readString(extractedFile.toPath())).isEqualTo("Test content");
    }

    @Test
    void testIsValidJsonValidObject() {
        String validJson = "{\"key\": \"value\", \"number\": 42}";
        boolean result = DumCommonsUtils.isValidJson(validJson);

        assertThat(result).isTrue();
    }

    @Test
    void testIsValidJsonValidArray() {
        String validJsonArray = "[{\"key\": \"value1\"}, {\"key\": \"value2\"}]";
        boolean result = DumCommonsUtils.isValidJson(validJsonArray);

        assertThat(result).isTrue();
    }

    @Test
    void testIsValidJsonInvalidJson() {
        String invalidJson = "{invalid json structure";
        boolean result = DumCommonsUtils.isValidJson(invalidJson);

        assertThat(result).isFalse();
    }

    @Test
    void testAddSubDirToStoreDir() {
        String subDirName = "testSubDir";
        File subDir = DumCommonsUtils.addSubDirToStoreDir(subDirName);

        assertThat(subDir)
                .isNotNull()
                .hasName(subDirName)
                .exists();

        // Clean up
        assertThat(subDir.delete()).isTrue();
    }

    @Test
    void testGetTempDirectory() {
        File tempDir = DumCommonsUtils.getTempDirectory();

        assertThat(tempDir)
                .isNotNull()
                .hasName("tmp")
                .exists();
    }

    // Helper class for JSON testing
    public static class TestObject {
        private final String name;
        private final int value;

        public TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }
    }
}