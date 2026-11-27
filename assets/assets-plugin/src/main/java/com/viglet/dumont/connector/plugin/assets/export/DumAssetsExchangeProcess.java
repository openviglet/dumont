package com.viglet.dumont.connector.plugin.assets.export;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

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
import com.viglet.dumont.connector.plugin.assets.export.bean.DumAssetsExchange;
import com.viglet.dumont.connector.plugin.assets.export.bean.DumAssetsSourceExchange;
import com.viglet.dumont.connector.plugin.assets.persistence.model.DumAssetsSource;
import com.viglet.dumont.connector.plugin.assets.persistence.repository.DumAssetsSourceRepository;
import com.viglet.dumont.spring.utils.DumSpringUtils;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j @Component @Transactional
public class DumAssetsExchangeProcess {
    private static final String EXPORT_FILE = "export.json";
    private final DumAssetsSourceRepository repository;

    public DumAssetsExchangeProcess(DumAssetsSourceRepository repository) {
        this.repository = repository;
    }

    public StreamingResponseBody exportObject(HttpServletResponse response) {
        String folderName = UUID.randomUUID().toString();
        File userDir = new File(System.getProperty("user.dir"));
        if (!userDir.exists() || !userDir.isDirectory()) return null;

        File tmpDir = new File(userDir, "store/tmp");
        try { Files.createDirectories(tmpDir.toPath()); } catch (IOException e) { log.error(e.getMessage(), e); }

        List<DumAssetsSource> sources = repository.findAll();
        File exportDir = new File(tmpDir, folderName);
        File exportFile = new File(exportDir, EXPORT_FILE);
        try { Files.createDirectories(exportDir.toPath()); } catch (IOException e) { log.error(e.getMessage(), e); }

        JsonMapper mapper = JsonMapper.builder()
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(exportFile,
                    new DumAssetsExchange(sources.stream().map(s -> DumAssetsSourceExchange.builder()
                            .id(s.getId()).name(s.getName()).description(s.getDescription())
                            .sourceDir(s.getSourceDir()).prefixFromReplace(s.getPrefixFromReplace())
                            .prefixToReplace(s.getPrefixToReplace()).site(s.getSite()).locale(s.getLocale())
                            .contentType(s.getContentType()).chunk(s.getChunk()).typeInId(s.isTypeInId())
                            .fileSizeField(s.getFileSizeField()).fileExtensionField(s.getFileExtensionField())
                            .encoding(s.getEncoding()).showOutput(s.isShowOutput())
                            .turSNSites(new HashSet<>(s.getTurSNSites())).build()).toList()));

            File zipFile = new File(tmpDir, folderName + ".zip");
            DumCommonsUtils.addFilesToZip(exportDir, zipFile);
            String zipFileName = "Assets_" + new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date()) + ".zip";
            response.addHeader("Content-disposition", "attachment;filename=" + zipFileName);
            response.setContentType("application/octet-stream");
            response.setStatus(HttpServletResponse.SC_OK);
            return output -> {
                try {
                    output.write(Files.readAllBytes(Paths.get(zipFile.getAbsolutePath())));
                    output.flush();
                    FileUtils.deleteDirectory(exportDir);
                    FileUtils.deleteQuietly(zipFile);
                } catch (Exception e) { log.error(e.getMessage(), e); }
            };
        } catch (Exception e) { log.error(e.getMessage(), e); }
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
            tmpFile = tmpDir.resolve("assets-import-" + java.util.UUID.randomUUID() + ".json").toFile();
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
            for (File f : Objects.requireNonNull(extractFolder.listFiles())) {
                if (f.isDirectory() && new File(f, EXPORT_FILE).exists()) {
                    parentExtractFolder = extractFolder;
                    extractFolder = f;
                }
            }
        }
        try {
            return action.apply(new File(extractFolder, EXPORT_FILE));
        } finally {
            try {
                FileUtils.deleteDirectory(extractFolder);
                if (parentExtractFolder != null) FileUtils.deleteDirectory(parentExtractFolder);
            } catch (IOException e) { log.error(e.getMessage(), e); }
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
        DumAssetsExchange exchange;
        try {
            ObjectMapper mapper = JsonMapper.builder()
                    .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false).build();
            exchange = mapper.readValue(exportFile, DumAssetsExchange.class);
        } catch (StreamReadException | DatabindException | JacksonIOException e) {
            if (mode != DumSourceImportMode.ADD) {
                throw new DumSourceImportException("The file could not be read as a source export.", e);
            }
            log.error(e.getMessage(), e);
            return;
        }
        if (mode == DumSourceImportMode.REPLACE) {
            replaceSources(exchange);
        } else if (mode == DumSourceImportMode.MERGE) {
            mergeSources(exchange);
        } else {
            importAssetsSources(exchange);
        }
    }

    /** Adds the file's sources, skipping any whose id exists or whose name cannot be saved. */
    public void importAssetsSources(DumAssetsExchange exchange) {
        if (exchange.getSources() != null && !exchange.getSources().isEmpty()) {
            exchange.getSources().stream()
                    .filter(s -> hasValidName(s.getName()))
                    .filter(s -> s.getId() == null || s.getId().isEmpty() || repository.findById(s.getId()).isEmpty())
                    .forEach(this::importSource);
        }
    }

    /**
     * The file becomes the whole set of assets sources (roadmap DU9): every existing source is
     * deleted and the file's are created, inside this class's transaction, so a failure leaves the
     * instance as it was. A file holding a name that cannot be saved is refused before anything is
     * deleted. Ids are left to the generator; names are kept, and indexing history is keyed by name.
     */
    public DumSourceReplacePlan replaceSources(DumAssetsExchange exchange) {
        List<DumAssetsSourceExchange> sources = exchange.getSources() == null ? List.of()
                : List.copyOf(exchange.getSources());
        DumSourceReplacePlan plan = planReplace(sources);
        if (!plan.isApplicable()) {
            throw plan.refusal();
        }
        log.info("Replacing the assets sources: create {}, overwrite {}, remove {}", plan.create(),
                plan.overwrite(), plan.remove());
        repository.deleteAll();
        repository.flush();
        sources.forEach(source -> source.setId(null));
        if (!sources.isEmpty()) {
            importAssetsSources(exchange);
        }
        return plan;
    }

    /**
     * The file updates the assets sources it names and keeps the others (roadmap DU14): a source
     * whose name is in the file is deleted and recreated from it, a new name is created, and the rest
     * are left alone, inside this class's transaction. A file holding a name that cannot be saved is
     * refused before anything is deleted.
     */
    public DumSourceReplacePlan mergeSources(DumAssetsExchange exchange) {
        List<DumAssetsSourceExchange> sources = exchange.getSources() == null ? List.of()
                : List.copyOf(exchange.getSources());
        DumSourceReplacePlan plan = planReplace(sources).forMode(DumSourceImportMode.MERGE);
        if (!plan.isApplicable()) {
            throw plan.refusal();
        }
        log.info("Merging the assets sources: create {}, overwrite {}", plan.create(), plan.overwrite());
        repository.deleteAll(repository.findAll().stream()
                .filter(source -> plan.overwrite().contains(source.getName())).toList());
        repository.flush();
        sources.forEach(source -> source.setId(null));
        if (!sources.isEmpty()) {
            importAssetsSources(exchange);
        }
        return plan;
    }

    /** What replacing with {@code exportFile} would do (roadmap DU10), without writing anything. */
    public DumSourceReplacePlan planFromFile(File exportFile) {
        return planFromFile(exportFile, DumSourceImportMode.REPLACE);
    }

    /** What importing {@code exportFile} in {@code mode} would do (roadmap DU14), without writing. */
    public DumSourceReplacePlan planFromFile(File exportFile, DumSourceImportMode mode) {
        DumAssetsExchange exchange;
        try {
            exchange = JsonMapper.builder()
                    .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false).build().readValue(exportFile, DumAssetsExchange.class);
        } catch (RuntimeException e) {
            throw new DumSourceImportException("The file could not be read as a source export.", e);
        }
        return planReplace(exchange.getSources() == null ? List.<DumAssetsSourceExchange>of() : List.copyOf(exchange.getSources()))
                .forMode(mode);
    }

    /** What replacing the assets sources with {@code sources} would do, without writing anything. */
    public DumSourceReplacePlan planReplace(List<DumAssetsSourceExchange> sources) {
        return DumSourceReplacePlan.of(sources.stream().map(DumAssetsSourceExchange::getName).toList(),
                repository.findAll().stream().map(DumAssetsSource::getName).toList(), true);
    }

    private boolean hasValidName(String name) {
        if (DumSourceNames.isValid(name)) {
            return true;
        }
        log.warn("Skipping the source {}: {}", name, DumSourceNames.RULE);
        return false;
    }

    private void importSource(DumAssetsSourceExchange s) {
        repository.save(DumAssetsSource.builder()
                .name(s.getName()).description(s.getDescription()).sourceDir(s.getSourceDir())
                .prefixFromReplace(s.getPrefixFromReplace()).prefixToReplace(s.getPrefixToReplace())
                .site(s.getSite()).locale(s.getLocale()).contentType(s.getContentType())
                .chunk(s.getChunk()).typeInId(s.isTypeInId()).fileSizeField(s.getFileSizeField())
                .fileExtensionField(s.getFileExtensionField()).encoding(s.getEncoding())
                .showOutput(s.isShowOutput())
                .turSNSites(s.getTurSNSites() != null ? new HashSet<>(s.getTurSNSites()) : new HashSet<>())
                .build());
        log.info("Imported Assets source: {}", s.getName());
    }
}
