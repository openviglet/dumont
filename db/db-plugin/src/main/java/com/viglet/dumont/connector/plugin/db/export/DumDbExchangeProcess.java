/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.plugin.db.export;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.viglet.dumont.commons.utils.DumCommonsUtils;
import com.viglet.dumont.connector.plugin.db.export.bean.DumDbExchange;
import com.viglet.dumont.connector.plugin.db.export.bean.DumDbSourceExchange;
import com.viglet.dumont.connector.plugin.db.persistence.model.DumDbSource;
import com.viglet.dumont.connector.plugin.db.persistence.repository.DumDbSourceRepository;
import com.viglet.dumont.spring.utils.DumSpringUtils;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@Transactional
public class DumDbExchangeProcess {
    private static final String EXPORT_FILE = "export.json";
    private final DumDbSourceRepository dumDbSourceRepository;

    public DumDbExchangeProcess(DumDbSourceRepository dumDbSourceRepository) {
        this.dumDbSourceRepository = dumDbSourceRepository;
    }

    public StreamingResponseBody exportObject(HttpServletResponse response) {
        String folderName = UUID.randomUUID().toString();
        File userDir = new File(System.getProperty("user.dir"));
        if (userDir.exists() && userDir.isDirectory()) {
            File tmpDir = new File(userDir.getAbsolutePath().concat(File.separator + "store" + File.separator + "tmp"));
            try {
                Files.createDirectories(tmpDir.toPath());
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
            List<DumDbSource> sources = dumDbSourceRepository.findAll();
            File exportDir = new File(tmpDir.getAbsolutePath().concat(File.separator + folderName));
            File exportFile = new File(exportDir.getAbsolutePath().concat(File.separator + EXPORT_FILE));
            try {
                Files.createDirectories(exportDir.toPath());
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
            JsonMapper mapper = JsonMapper.builder()
                    .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                    .build();
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(exportFile,
                        new DumDbExchange(sources.stream()
                                .map(source -> DumDbSourceExchange.builder()
                                        .id(source.getId())
                                        .name(source.getName())
                                        .description(source.getDescription())
                                        .driver(source.getDriver())
                                        .url(source.getUrl())
                                        .dbUsername(source.getDbUsername())
                                        .dbPassword(source.getDbPassword())
                                        .query(source.getQuery())
                                        .site(source.getSite())
                                        .locale(source.getLocale())
                                        .contentType(source.getContentType())
                                        .chunk(source.getChunk())
                                        .typeInId(source.isTypeInId())
                                        .multiValuedSeparator(source.getMultiValuedSeparator())
                                        .multiValuedFields(source.getMultiValuedFields())
                                        .removeHtmlTagsFields(source.getRemoveHtmlTagsFields())
                                        .filePathField(source.getFilePathField())
                                        .fileContentField(source.getFileContentField())
                                        .fileSizeField(source.getFileSizeField())
                                        .customClassName(source.getCustomClassName())
                                        .maxContentMegaByteSize(source.getMaxContentMegaByteSize())
                                        .encoding(source.getEncoding())
                                        .showOutput(source.isShowOutput())
                                        .deindexBeforeImporting(source.isDeindexBeforeImporting())
                                        .turSNSites(new HashSet<>(source.getTurSNSites()))
                                        .build())
                                .toList()));
                File zipFile = new File(tmpDir.getAbsolutePath().concat(File.separator + folderName + ".zip"));
                DumCommonsUtils.addFilesToZip(exportDir, zipFile);
                String strDate = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date());
                String zipFileName = "Db_" + strDate + ".zip";
                response.addHeader("Content-disposition", "attachment;filename=" + zipFileName);
                response.setContentType("application/octet-stream");
                response.setStatus(HttpServletResponse.SC_OK);

                return output -> {
                    try {
                        java.nio.file.Path path = Paths.get(zipFile.getAbsolutePath());
                        byte[] data = Files.readAllBytes(path);
                        output.write(data);
                        output.flush();
                        FileUtils.deleteDirectory(exportDir);
                        FileUtils.deleteQuietly(zipFile);
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                };
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        return null;
    }

    public void importFromMultipartFile(MultipartFile multipartFile) {
        File extractFolder = DumSpringUtils.extractZipFile(multipartFile);
        File parentExtractFolder = null;
        if (!(new File(extractFolder, EXPORT_FILE).exists())
                && Objects.requireNonNull(extractFolder.listFiles()).length == 1) {
            for (File fileOrDirectory : Objects.requireNonNull(extractFolder.listFiles())) {
                if (fileOrDirectory.isDirectory() && new File(fileOrDirectory, EXPORT_FILE).exists()) {
                    parentExtractFolder = extractFolder;
                    extractFolder = fileOrDirectory;
                }
            }
        }
        File exportFile = new File(extractFolder.getAbsolutePath().concat(File.separator + EXPORT_FILE));
        importFromFile(exportFile);
        try {
            FileUtils.deleteDirectory(extractFolder);
            if (parentExtractFolder != null) {
                FileUtils.deleteDirectory(parentExtractFolder);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public void importFromFile(File exportFile) {
        log.info("Importing {} file", exportFile);
        try {
            ObjectMapper mapper = JsonMapper.builder()
                    .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
                    .build();
            DumDbExchange dumDbExchange = mapper.readValue(exportFile, DumDbExchange.class);
            if (hasSources(dumDbExchange)) {
                importDbSources(dumDbExchange);
            }
        } catch (StreamReadException | DatabindException | JacksonIOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private static boolean hasSources(DumDbExchange dumDbExchange) {
        return dumDbExchange.getSources() != null && !dumDbExchange.getSources().isEmpty();
    }

    public void importDbSources(DumDbExchange dumDbExchange) {
        dumDbExchange.getSources()
                .stream()
                .filter(sourceExchange -> sourceExchange.getId() == null
                        || sourceExchange.getId().isEmpty()
                        || dumDbSourceRepository.findById(sourceExchange.getId()).isEmpty())
                .forEach(this::importSource);
    }

    private void importSource(DumDbSourceExchange sourceExchange) {
        DumDbSource source = DumDbSource.builder()
                .name(sourceExchange.getName())
                .description(sourceExchange.getDescription())
                .driver(sourceExchange.getDriver())
                .url(sourceExchange.getUrl())
                .dbUsername(sourceExchange.getDbUsername())
                .dbPassword(sourceExchange.getDbPassword())
                .query(sourceExchange.getQuery())
                .site(sourceExchange.getSite())
                .locale(sourceExchange.getLocale())
                .contentType(sourceExchange.getContentType())
                .chunk(sourceExchange.getChunk())
                .typeInId(sourceExchange.isTypeInId())
                .multiValuedSeparator(sourceExchange.getMultiValuedSeparator())
                .multiValuedFields(sourceExchange.getMultiValuedFields())
                .removeHtmlTagsFields(sourceExchange.getRemoveHtmlTagsFields())
                .filePathField(sourceExchange.getFilePathField())
                .fileContentField(sourceExchange.getFileContentField())
                .fileSizeField(sourceExchange.getFileSizeField())
                .customClassName(sourceExchange.getCustomClassName())
                .maxContentMegaByteSize(sourceExchange.getMaxContentMegaByteSize())
                .encoding(sourceExchange.getEncoding())
                .showOutput(sourceExchange.isShowOutput())
                .deindexBeforeImporting(sourceExchange.isDeindexBeforeImporting())
                .turSNSites(sourceExchange.getTurSNSites() != null
                        ? new HashSet<>(sourceExchange.getTurSNSites())
                        : new HashSet<>())
                .build();
        dumDbSourceRepository.save(source);
        log.info("Imported DB source: {}", source.getName());
    }
}
