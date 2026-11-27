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
import com.viglet.dumont.connector.commons.exchange.DumSourceReplacePlan;
import com.viglet.dumont.connector.commons.exchange.DumSourceImportMode;
import com.viglet.dumont.connector.commons.exchange.DumSourceImportException;
import com.viglet.dumont.connector.commons.DumSourceNames;
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
        importFromMultipartFile(multipartFile, DumSourceImportMode.ADD);
    }

    /** An uploaded zip, imported in {@code mode} (roadmap DU9). */
    public void importFromMultipartFile(MultipartFile multipartFile, DumSourceImportMode mode) {
        withExportFile(multipartFile, exportFile -> {
            importFromFile(exportFile, mode);
            return null;
        });
    }

    /**
     * What importing the uploaded file in {@code mode} would do (roadmap DU10, DU14): the file is
     * read and matched by the same code the import runs, and nothing is written.
     */
    public DumSourceReplacePlan planFromMultipartFile(MultipartFile multipartFile, DumSourceImportMode mode) {
        return withExportFile(multipartFile, exportFile -> planFromFile(exportFile, mode));
    }

    /**
     * A plain {@code .json} upload (roadmap DU11): an {@code export.json} taken out of its zip, read
     * through the same path as the zip.
     */
    private <T> T withJsonUpload(MultipartFile multipartFile, java.util.function.Function<File, T> action) {
        File tmpFile = null;
        try {
            java.nio.file.Path tmpDir = java.nio.file.Paths.get(System.getProperty("user.dir"), "store", "tmp");
            java.nio.file.Files.createDirectories(tmpDir);
            tmpFile = tmpDir.resolve("db-import-" + java.util.UUID.randomUUID() + ".json").toFile();
            multipartFile.transferTo(tmpFile);
            return action.apply(tmpFile);
        } catch (IOException e) {
            throw new DumSourceImportException("The uploaded file could not be read.", e);
        } finally {
            FileUtils.deleteQuietly(tmpFile);
        }
    }

    /** Runs {@code action} on the upload's {@code export.json}, and cleans up what was extracted. */
    private <T> T withExportFile(MultipartFile multipartFile, java.util.function.Function<File, T> action) {
        String originalName = multipartFile.getOriginalFilename();
        if (originalName != null && originalName.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
            return withJsonUpload(multipartFile, action);
        }
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
        try {
            return action.apply(exportFile);
        } finally {
            try {
                FileUtils.deleteDirectory(extractFolder);
                if (parentExtractFolder != null) {
                    FileUtils.deleteDirectory(parentExtractFolder);
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    public void importFromFile(File exportFile) {
        importFromFile(exportFile, DumSourceImportMode.ADD);
    }

    /**
     * Reads {@code exportFile} and imports it in {@code mode}. An unreadable file is logged and
     * skipped when adding, and refuses the whole import when replacing, before anything is deleted.
     */
    public void importFromFile(File exportFile, DumSourceImportMode mode) {
        log.info("Importing {} file ({})", exportFile, mode);
        DumDbExchange dumDbExchange;
        try {
            ObjectMapper mapper = JsonMapper.builder()
                    .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
                    .build();
            dumDbExchange = mapper.readValue(exportFile, DumDbExchange.class);
        } catch (StreamReadException | DatabindException | JacksonIOException e) {
            if (mode != DumSourceImportMode.ADD) {
                throw new DumSourceImportException("The file could not be read as a source export.", e);
            }
            log.error(e.getMessage(), e);
            return;
        }
        if (mode == DumSourceImportMode.REPLACE) {
            replaceSources(dumDbExchange);
        } else if (mode == DumSourceImportMode.MERGE) {
            mergeSources(dumDbExchange);
        } else if (hasSources(dumDbExchange)) {
            importDbSources(dumDbExchange);
        }
    }

    /**
     * The file updates the DB sources it names and keeps the others (roadmap DU14): a source whose
     * name is in the file is deleted and recreated from it, a new name is created, and the rest are
     * left alone, inside this class's transaction. A file holding a name that cannot be saved is
     * refused before anything is deleted.
     */
    public DumSourceReplacePlan mergeSources(DumDbExchange exchange) {
        List<DumDbSourceExchange> sources = exchange.getSources() == null ? List.of()
                : List.copyOf(exchange.getSources());
        DumSourceReplacePlan plan = planReplace(sources).forMode(DumSourceImportMode.MERGE);
        if (!plan.isApplicable()) {
            throw plan.refusal();
        }
        log.info("Merging the DB sources: create {}, overwrite {}", plan.create(), plan.overwrite());
        dumDbSourceRepository.deleteAll(dumDbSourceRepository.findAll().stream()
                .filter(source -> plan.overwrite().contains(source.getName())).toList());
        dumDbSourceRepository.flush();
        sources.forEach(source -> source.setId(null));
        if (!sources.isEmpty()) {
            importDbSources(exchange);
        }
        return plan;
    }

    /**
     * The file becomes the whole set of DB sources (roadmap DU9): every existing source is
     * deleted and the file's are created, inside this class's transaction, so a failure leaves the
     * instance as it was. A file holding a name that cannot be saved is refused before anything is
     * deleted. Ids are left to the generator; names are kept, and indexing history is keyed by name.
     */
    public DumSourceReplacePlan replaceSources(DumDbExchange exchange) {
        List<DumDbSourceExchange> sources = exchange.getSources() == null ? List.of()
                : List.copyOf(exchange.getSources());
        DumSourceReplacePlan plan = planReplace(sources);
        if (!plan.isApplicable()) {
            throw plan.refusal();
        }
        log.info("Replacing the DB sources: create {}, overwrite {}, remove {}", plan.create(),
                plan.overwrite(), plan.remove());
        dumDbSourceRepository.deleteAll();
        dumDbSourceRepository.flush();
        sources.forEach(source -> source.setId(null));
        if (!sources.isEmpty()) {
            importDbSources(exchange);
        }
        return plan;
    }

    /** What replacing with {@code exportFile} would do (roadmap DU10), without writing anything. */
    public DumSourceReplacePlan planFromFile(File exportFile) {
        return planFromFile(exportFile, DumSourceImportMode.REPLACE);
    }

    /** What importing {@code exportFile} in {@code mode} would do (roadmap DU14), without writing. */
    public DumSourceReplacePlan planFromFile(File exportFile, DumSourceImportMode mode) {
        DumDbExchange exchange;
        try {
            exchange = JsonMapper.builder()
                    .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false).build().readValue(exportFile, DumDbExchange.class);
        } catch (RuntimeException e) {
            throw new DumSourceImportException("The file could not be read as a source export.", e);
        }
        return planReplace(exchange.getSources() == null ? List.<DumDbSourceExchange>of() : List.copyOf(exchange.getSources()))
                .forMode(mode);
    }

    /** What replacing the DB sources with {@code sources} would do, without writing anything. */
    public DumSourceReplacePlan planReplace(List<DumDbSourceExchange> sources) {
        return DumSourceReplacePlan.of(sources.stream().map(DumDbSourceExchange::getName).toList(),
                dumDbSourceRepository.findAll().stream().map(DumDbSource::getName).toList(), true);
    }

    private static boolean hasSources(DumDbExchange dumDbExchange) {
        return dumDbExchange.getSources() != null && !dumDbExchange.getSources().isEmpty();
    }

    public void importDbSources(DumDbExchange dumDbExchange) {
        dumDbExchange.getSources()
                .stream()
                .filter(sourceExchange -> hasValidName(sourceExchange.getName()))
                .filter(sourceExchange -> sourceExchange.getId() == null
                        || sourceExchange.getId().isEmpty()
                        || dumDbSourceRepository.findById(sourceExchange.getId()).isEmpty())
                .forEach(this::importSource);
    }

    private boolean hasValidName(String name) {
        if (DumSourceNames.isValid(name)) {
            return true;
        }
        log.warn("Skipping the source {}: {}", name, DumSourceNames.RULE);
        return false;
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
