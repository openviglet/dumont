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
package com.viglet.dumont.connector.commons.exchange;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The shapes a source import accepts, detected from the upload's content rather than its file name
 * (roadmap <strong>DU15</strong>): a zip holding {@code export.json}, an {@code export.json} with a
 * {@code sources} array, or a single source object, which is wrapped as a one-source exchange. Any
 * other upload is refused with a message naming those shapes, so an import never silently does
 * nothing.
 */
public final class DumSourceExchangeFile {

    private static final byte[] ZIP_MAGIC = { 'P', 'K', 3, 4 };
    private static final String SOURCES = "sources";

    private DumSourceExchangeFile() {
    }

    /** The shapes an import accepts, for a refusal to name; {@code nameField} is the source's key. */
    public static String accepted(String nameField) {
        return "a zip holding export.json, an export.json with a \"sources\" array, or a single source"
                + " object with a \"" + nameField + "\"";
    }

    /** Whether {@code content} starts as a zip archive does; the stream is read, not closed. */
    public static boolean isZip(InputStream content) throws IOException {
        byte[] head = content.readNBytes(ZIP_MAGIC.length);
        if (head.length < ZIP_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < ZIP_MAGIC.length; i++) {
            if (head[i] != ZIP_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Makes {@code exportFile} an exchange with a {@code sources} array, rewriting it in place when it
     * holds a single source object. Only call this on a copy of the upload: the file may change.
     *
     * @param nameField the key that names a source of this connector ({@code name}, or {@code title}
     *                  for the web crawler)
     * @throws DumSourceImportException when the file is missing, is not JSON, or is none of the
     *                                  accepted shapes
     */
    public static void normalize(File exportFile, String nameField) {
        if (!exportFile.isFile()) {
            throw refusal("The upload holds no export.json", nameField);
        }
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode root;
        try {
            root = mapper.readTree(exportFile);
        } catch (JacksonException e) {
            throw new DumSourceImportException(message("The file is not valid JSON", nameField), e);
        }
        if (root == null || !root.isObject()) {
            throw refusal("The file is not a source export", nameField);
        }
        if (root.has(SOURCES)) {
            if (!root.get(SOURCES).isArray()) {
                throw refusal("The file's \"sources\" is not an array", nameField);
            }
            return;
        }
        JsonNode name = root.get(nameField);
        if (name == null || !name.isString() || name.asString().isBlank()) {
            throw refusal("The file is not a source export", nameField);
        }
        ObjectNode exchange = mapper.createObjectNode();
        exchange.set(SOURCES, mapper.createArrayNode().add(root));
        mapper.writeValue(exportFile, exchange);
    }

    private static DumSourceImportException refusal(String reason, String nameField) {
        return new DumSourceImportException(message(reason, nameField));
    }

    private static String message(String reason, String nameField) {
        return reason + "; nothing was imported. Accepted: " + accepted(nameField) + ".";
    }
}
