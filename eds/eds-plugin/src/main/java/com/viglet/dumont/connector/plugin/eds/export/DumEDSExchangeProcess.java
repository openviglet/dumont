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

package com.viglet.dumont.connector.plugin.eds.export;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.viglet.dumont.commons.utils.DumCommonsUtils;
import com.viglet.dumont.connector.plugin.eds.export.bean.DumEDSExchange;
import com.viglet.dumont.connector.plugin.eds.export.bean.DumEDSFacetExchange;
import com.viglet.dumont.connector.plugin.eds.export.bean.DumEDSFieldMappingExchange;
import com.viglet.dumont.connector.plugin.eds.export.bean.DumEDSSourceExchange;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSExcludePattern;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSFacet;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSFieldMapping;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSIndex;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSRemoveSelector;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSExcludePatternRepository;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSFacetRepository;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSFieldMappingRepository;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSIndexRepository;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSRemoveSelectorRepository;
import com.viglet.dumont.connector.plugin.eds.persistence.repository.DumEDSSourceRepository;
import com.viglet.dumont.spring.utils.DumSpringUtils;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Round-trips EDS source configuration between the database and a portable ZIP
 * containing a single {@code export.json} entry. Used by:
 *
 * <ul>
 *   <li>{@code GET  /api/v2/eds/source/export} — streams a ZIP to the browser</li>
 *   <li>{@code POST /api/v2/eds/source/import} — accepts a multipart ZIP upload</li>
 *   <li>{@link com.viglet.dumont.connector.plugin.eds.onstartup.DumEDSOnStartupJpa}
 *       — first-boot import of {@code $user.dir/export/*.json}</li>
 * </ul>
 *
 * <p>The on-disk filename pattern is {@code EDS_yyyy-MM-dd_HHmmss.zip} to match
 * the sibling WC/DB/AEM exports so devops scripts can pattern-match them all.
 */
@Slf4j
@Component
@Transactional
public class DumEDSExchangeProcess {

    static final String EXPORT_FILE = "export.json";

    private final DumEDSSourceRepository dumEDSSourceRepository;
    private final DumEDSIndexRepository dumEDSIndexRepository;
    private final DumEDSExcludePatternRepository dumEDSExcludePatternRepository;
    private final DumEDSRemoveSelectorRepository dumEDSRemoveSelectorRepository;
    private final DumEDSFacetRepository dumEDSFacetRepository;
    private final DumEDSFieldMappingRepository dumEDSFieldMappingRepository;

    public DumEDSExchangeProcess(DumEDSSourceRepository dumEDSSourceRepository,
            DumEDSIndexRepository dumEDSIndexRepository,
            DumEDSExcludePatternRepository dumEDSExcludePatternRepository,
            DumEDSRemoveSelectorRepository dumEDSRemoveSelectorRepository,
            DumEDSFacetRepository dumEDSFacetRepository,
            DumEDSFieldMappingRepository dumEDSFieldMappingRepository) {
        this.dumEDSSourceRepository = dumEDSSourceRepository;
        this.dumEDSIndexRepository = dumEDSIndexRepository;
        this.dumEDSExcludePatternRepository = dumEDSExcludePatternRepository;
        this.dumEDSRemoveSelectorRepository = dumEDSRemoveSelectorRepository;
        this.dumEDSFacetRepository = dumEDSFacetRepository;
        this.dumEDSFieldMappingRepository = dumEDSFieldMappingRepository;
    }

    public StreamingResponseBody exportObject(HttpServletResponse response) {
        String folderName = UUID.randomUUID().toString();
        File userDir = new File(System.getProperty("user.dir"));
        if (!userDir.exists() || !userDir.isDirectory()) {
            return null;
        }
        File tmpDir = new File(userDir.getAbsolutePath()
                .concat(File.separator + "store" + File.separator + "tmp"));
        try {
            Files.createDirectories(tmpDir.toPath());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

        File exportDir = new File(tmpDir.getAbsolutePath().concat(File.separator + folderName));
        File exportFile = new File(exportDir.getAbsolutePath().concat(File.separator + EXPORT_FILE));
        try {
            Files.createDirectories(exportDir.toPath());
            DumEDSExchange exchange = buildExchangeFromDb();
            writeExportJson(exchange, exportFile);

            File zipFile = new File(tmpDir.getAbsolutePath()
                    .concat(File.separator + folderName + ".zip"));
            DumCommonsUtils.addFilesToZip(exportDir, zipFile);

            String stamp = LocalDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"));
            response.addHeader("Content-disposition",
                    "attachment;filename=EDS_" + stamp + ".zip");
            response.setContentType("application/octet-stream");
            response.setStatus(HttpServletResponse.SC_OK);

            return output -> writeZipToOutput(zipFile, exportDir, output);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    DumEDSExchange buildExchangeFromDb() {
        List<DumEDSSource> sources = dumEDSSourceRepository.findAll();
        return DumEDSExchange.builder()
                .sources(sources.stream().map(this::toExchange).toList())
                .build();
    }

    DumEDSSourceExchange toExchange(DumEDSSource source) {
        return DumEDSSourceExchange.builder()
                .id(source.getId())
                .title(source.getTitle())
                .description(source.getDescription())
                .locale(source.getLocale())
                .localeClass(source.getLocaleClass())
                .baseUrl(source.getBaseUrl())
                .sitemapFallback(source.getSitemapFallback())
                .bodySelector(source.getBodySelector())
                .userAgent(source.getUserAgent())
                .fetchTimeoutMs(source.getFetchTimeoutMs())
                .requestsPerSecond(source.getRequestsPerSecond())
                .maxConcurrent(source.getMaxConcurrent())
                .facetPathPattern(source.getFacetPathPattern())
                .turSNSites(source.getTurSNSites())
                .indexes(source.getIndexes().stream().map(DumEDSIndex::getPath).toList())
                .excludePatterns(source.getExcludePatterns().stream()
                        .map(DumEDSExcludePattern::getPattern).toList())
                .removeSelectors(source.getRemoveSelectors().stream()
                        .map(DumEDSRemoveSelector::getSelector).toList())
                .facets(source.getFacets().stream()
                        .map(f -> DumEDSFacetExchange.builder()
                                .name(f.getName())
                                .ordinal(f.getOrdinal())
                                .allowedValues(f.getAllowedValues())
                                .build())
                        .toList())
                .fieldMappings(source.getFieldMappings().stream()
                        .map(m -> DumEDSFieldMappingExchange.builder()
                                .sourceField(m.getSourceField())
                                .targetField(m.getTargetField())
                                .build())
                        .toList())
                .build();
    }

    private static void writeExportJson(DumEDSExchange exchange, File exportFile) {
        ObjectMapper mapper = JsonMapper.builder()
                .changeDefaultPropertyInclusion(incl -> JsonInclude.Value.construct(
                        JsonInclude.Include.NON_NULL, JsonInclude.Include.USE_DEFAULTS))
                .build();
        mapper.writerWithDefaultPrettyPrinter().writeValue(exportFile, exchange);
    }

    private static void writeZipToOutput(File zipFile, File exportDir,
            java.io.OutputStream output) {
        try {
            byte[] data = Files.readAllBytes(Paths.get(zipFile.getAbsolutePath()));
            output.write(data);
            output.flush();
            FileUtils.deleteDirectory(exportDir);
            FileUtils.deleteQuietly(zipFile);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public void importFromMultipartFile(MultipartFile multipartFile) {
        File extractFolder = DumSpringUtils.extractZipFile(multipartFile);
        File parentExtractFolder = null;
        File[] entries = extractFolder.listFiles();
        if (!new File(extractFolder, EXPORT_FILE).exists()
                && entries != null && entries.length == 1
                && entries[0].isDirectory()
                && new File(entries[0], EXPORT_FILE).exists()) {
            parentExtractFolder = extractFolder;
            extractFolder = entries[0];
        }
        File exportFile = new File(extractFolder.getAbsolutePath()
                .concat(File.separator + EXPORT_FILE));
        importFromFile(exportFile);
        deleteQuietly(extractFolder);
        if (parentExtractFolder != null) {
            deleteQuietly(parentExtractFolder);
        }
    }

    public void importFromFile(File exportFile) {
        ObjectMapper mapper = new ObjectMapper();
        DumEDSExchange exchange = mapper.readValue(exportFile, DumEDSExchange.class);
        if (exchange.getSources() != null && !exchange.getSources().isEmpty()) {
            importSources(exchange);
        }
    }

    public void importSources(DumEDSExchange exchange) {
        for (DumEDSSourceExchange sourceExchange : exchange.getSources()) {
            String id = sourceExchange.getId();
            if (id != null && !id.isEmpty() && dumEDSSourceRepository.findById(id).isPresent()) {
                // Idempotent: never re-import an existing source. Operators wanting
                // to overwrite must delete the source first via the REST API.
                continue;
            }
            persistSource(sourceExchange);
        }
    }

    private void persistSource(DumEDSSourceExchange sourceExchange) {
        DumEDSSource source = DumEDSSource.builder()
                .title(sourceExchange.getTitle())
                .description(sourceExchange.getDescription())
                .baseUrl(sourceExchange.getBaseUrl())
                .locale(sourceExchange.getLocale())
                .localeClass(sourceExchange.getLocaleClass())
                .sitemapFallback(sourceExchange.getSitemapFallback())
                .bodySelector(sourceExchange.getBodySelector())
                .userAgent(sourceExchange.getUserAgent())
                .fetchTimeoutMs(sourceExchange.getFetchTimeoutMs())
                .requestsPerSecond(sourceExchange.getRequestsPerSecond())
                .maxConcurrent(sourceExchange.getMaxConcurrent())
                .facetPathPattern(sourceExchange.getFacetPathPattern())
                .turSNSites(sourceExchange.getTurSNSites())
                .build();
        dumEDSSourceRepository.save(source);

        emit(sourceExchange.getIndexes(), path ->
                dumEDSIndexRepository.save(new DumEDSIndex(path, source)));
        emit(sourceExchange.getExcludePatterns(), pattern ->
                dumEDSExcludePatternRepository.save(new DumEDSExcludePattern(pattern, source)));
        emit(sourceExchange.getRemoveSelectors(), selector ->
                dumEDSRemoveSelectorRepository.save(new DumEDSRemoveSelector(selector, source)));
        emitFacets(sourceExchange.getFacets(), source);
        emitFieldMappings(sourceExchange.getFieldMappings(), source);
    }

    private void emitFacets(Collection<DumEDSFacetExchange> facets, DumEDSSource source) {
        if (facets == null) {
            return;
        }
        for (DumEDSFacetExchange facet : facets) {
            dumEDSFacetRepository.save(new DumEDSFacet(
                    facet.getName(), facet.getOrdinal(), facet.getAllowedValues(), source));
        }
    }

    private void emitFieldMappings(Collection<DumEDSFieldMappingExchange> mappings,
            DumEDSSource source) {
        if (mappings == null) {
            return;
        }
        for (DumEDSFieldMappingExchange mapping : mappings) {
            dumEDSFieldMappingRepository.save(new DumEDSFieldMapping(
                    mapping.getSourceField(), mapping.getTargetField(), source));
        }
    }

    private static <T> void emit(Collection<T> values, java.util.function.Consumer<T> sink) {
        if (values == null) {
            return;
        }
        for (T value : values) {
            if (value != null) {
                sink.accept(value);
            }
        }
    }

    private static void deleteQuietly(File directory) {
        try {
            FileUtils.deleteDirectory(directory);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    /** Test-only: exposes the JSON serializer for round-trip assertions. */
    static String toJson(DumEDSExchange exchange) {
        ObjectMapper mapper = JsonMapper.builder()
                .changeDefaultPropertyInclusion(incl -> JsonInclude.Value.construct(
                        JsonInclude.Include.NON_NULL, JsonInclude.Include.USE_DEFAULTS))
                .build();
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(exchange);
    }

    /** Test-only: parses JSON into the exchange model. */
    static DumEDSExchange fromJson(String json) {
        return new ObjectMapper().readValue(json, DumEDSExchange.class);
    }
}
