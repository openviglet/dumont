/*
 * Copyright (C) 2016-2022 the original author or authors.
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections4.KeyValue;
import org.apache.commons.collections4.keyvalue.DefaultMapEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;

/**
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Slf4j
public class DumCommonsUtils {
    private static final String USER_DIR = "user.dir";
    private static final File userDir = new File(System.getProperty(USER_DIR));
    private static final String COLON = ":";

    private DumCommonsUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static Optional<KeyValue<String, String>> getKeyValueFromColon(String stringWithColon) {
        String[] attributeKV = stringWithColon.split(COLON);
        if (attributeKV.length >= 2) {
            String key = attributeKV[0];
            String value = Arrays.stream(attributeKV).skip(1).collect(Collectors.joining(COLON));
            return Optional.of(new DefaultMapEntry<>(key, value));
        } else {
            return Optional.empty();
        }
    }

    public static String html2Text(String text) {
        return Jsoup.parse(text).text();
    }

    /**
     * Add all files from the source directory to the destination zip file
     *
     * @param source      the directory with files to add
     * @param destination the zip file that should contain the files
     */
    public static void addFilesToZip(File source, File destination) {

        try (OutputStream archiveStream = Files.newOutputStream(destination.toPath());
                ArchiveOutputStream<ZipArchiveEntry> archive = new ArchiveStreamFactory()
                        .createArchiveOutputStream(ArchiveStreamFactory.ZIP, archiveStream)) {

            FileUtils.listFiles(source, null, true)
                    .forEach(file -> addFileToZip(source, archive, file));

            archive.finish();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private static void addFileToZip(File source, ArchiveOutputStream<ZipArchiveEntry> archive,
            File file) {
        String entryName;
        try {
            entryName = getEntryName(source, file);
            ZipArchiveEntry entry = new ZipArchiveEntry(entryName);
            archive.putArchiveEntry(entry);

            try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
                input.transferTo(archive);
                archive.closeArchiveEntry();
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * Remove the leading part of each entry that contains the source directory name
     *
     * @param source the directory where the file entry is found
     * @param file   the file that is about to be added
     * @return the name of an archive entry
     * @throws IOException if the io fails
     */
    private static String getEntryName(File source, File file) throws IOException {
        int index = source.getCanonicalPath().length() + 1;
        String path = file.getCanonicalPath();

        return path.substring(index);
    }

    private static File getStoreDir() {
        File store = new File(userDir.getAbsolutePath().concat(File.separator + "store"));
        try {
            Files.createDirectories(store.toPath());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return store;
    }

    public static File addSubDirToStoreDir(String directoryName) {
        File storeDir = getStoreDir();
        File newDir = new File(storeDir.getAbsolutePath().concat(File.separator + directoryName));
        try {
            Files.createDirectories(newDir.toPath());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return newDir;
    }

    /**
     * Unzip it
     *
     * @param file         input zip file
     * @param outputFolder output Folder
     */
    public static void unZipIt(File file, File outputFolder) {
        try (ZipFile zipFile = new ZipFile(file)) {
            zipFile.extractAll(outputFolder.getAbsolutePath());
        } catch (IllegalStateException | IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public static boolean isValidJson(String test) {
        try {
            new JSONObject(test);
        } catch (JSONException ex) {
            try {
                new JSONArray(test);
            } catch (JSONException ex1) {
                return false;
            }
        }
        return true;
    }

    public static File getTempDirectory() {
        return addSubDirToStoreDir("tmp");
    }
}
