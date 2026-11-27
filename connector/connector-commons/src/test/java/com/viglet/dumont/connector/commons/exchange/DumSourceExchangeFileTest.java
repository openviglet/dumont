package com.viglet.dumont.connector.commons.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Roadmap DU15: an import detects the upload's shape and refuses what it cannot read. */
class DumSourceExchangeFileTest {

    @TempDir
    Path dir;

    private File write(String json) throws IOException {
        Path file = dir.resolve("export.json");
        Files.writeString(file, json);
        return file.toFile();
    }

    @Test
    @DisplayName("a zip is told by its first bytes, not its name")
    void detectsAZipByContent() throws IOException {
        assertTrue(DumSourceExchangeFile.isZip(new ByteArrayInputStream(new byte[] { 'P', 'K', 3, 4, 0 })));
        assertFalse(DumSourceExchangeFile.isZip(
                new ByteArrayInputStream("{\"sources\":[]}".getBytes(StandardCharsets.UTF_8))));
        assertFalse(DumSourceExchangeFile.isZip(new ByteArrayInputStream(new byte[] { 'P' })));
    }

    @Test
    @DisplayName("an export.json with sources is left as it is")
    void keepsTheSourcesShape() throws IOException {
        String json = "{\"sources\":[{\"name\":\"wknd\"}]}";
        File file = write(json);

        DumSourceExchangeFile.normalize(file, "name");

        assertEquals(json, Files.readString(file.toPath()));
    }

    @Test
    @DisplayName("a single source object is wrapped as a one-source exchange")
    void wrapsASingleSource() throws IOException {
        File file = write("{\"title\":\"Viglet site\",\"url\":\"https://viglet.com\"}");

        DumSourceExchangeFile.normalize(file, "title");

        JsonNode root = JsonMapper.builder().build().readTree(file);
        assertEquals(1, root.get("sources").size());
        assertEquals("Viglet site", root.get("sources").get(0).get("title").asString());
        assertEquals("https://viglet.com", root.get("sources").get(0).get("url").asString());
    }

    @Test
    @DisplayName("anything else is refused, naming the accepted shapes")
    void refusesOtherShapes() throws IOException {
        for (String json : new String[] { "{ not json", "[]", "{\"targetAttrDefinitions\":[]}",
                "{\"name\":\"  \"}", "{\"sources\":{}}" }) {
            File file = write(json);
            var refused = assertThrows(DumSourceImportException.class,
                    () -> DumSourceExchangeFile.normalize(file, "name"), json);
            assertTrue(refused.getMessage().contains("single source object with a \"name\""), json);
        }
        assertThrows(DumSourceImportException.class,
                () -> DumSourceExchangeFile.normalize(dir.resolve("missing.json").toFile(), "name"));
    }
}
