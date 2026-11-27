package com.viglet.dumont.connector.plugin.webcrawler.export;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
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
import com.viglet.dumont.connector.commons.exchange.DumSourceExchangeFile;
import com.viglet.dumont.connector.commons.exchange.DumSourceImportMode;
import com.viglet.dumont.connector.commons.exchange.DumSourceImportException;
import com.viglet.dumont.commons.utils.DumCommonsUtils;
import com.viglet.dumont.connector.plugin.webcrawler.export.bean.DumWCAttribExchange;
import com.viglet.dumont.connector.plugin.webcrawler.export.bean.DumWCExchange;
import com.viglet.dumont.connector.plugin.webcrawler.export.bean.DumWCSourceExchange;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCAllowUrl;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCAttributeMapping;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCFileExtension;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCNotAllowUrl;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCSource;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCStartingPoint;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCUrl;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCAllowUrlRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCAttributeMappingRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCFileExtensionRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCNotAllowUrlRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCSourceRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCStartingPointRepository;
import com.viglet.dumont.spring.utils.DumSpringUtils;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@Transactional
public class DumWCExchangeProcess {
    private static final String EXPORT_FILE = "export.json";
    private final DumWCSourceRepository dumWCSourceRepository;

    private final DumWCAllowUrlRepository dumWCAllowUrlRepository;
    private final DumWCStartingPointRepository dumWCStartingPointRepository;
    private final DumWCNotAllowUrlRepository dumWCNotAllowUrlRepository;
    private final DumWCFileExtensionRepository dumWCFileExtensionRepository;
    private final DumWCAttributeMappingRepository dumWCAttributeMappingRepository;

    public DumWCExchangeProcess(DumWCSourceRepository dumWCSourceRepository,
            DumWCAllowUrlRepository dumWCAllowUrlRepository,
            DumWCStartingPointRepository dumWCStartingPointRepository,
            DumWCNotAllowUrlRepository dumWCNotAllowUrlRepository,
            DumWCFileExtensionRepository dumWCFileExtensionRepository,
            DumWCAttributeMappingRepository dumWCAttributeMappingRepository) {
        this.dumWCSourceRepository = dumWCSourceRepository;
        this.dumWCAllowUrlRepository = dumWCAllowUrlRepository;
        this.dumWCStartingPointRepository = dumWCStartingPointRepository;
        this.dumWCNotAllowUrlRepository = dumWCNotAllowUrlRepository;
        this.dumWCFileExtensionRepository = dumWCFileExtensionRepository;
        this.dumWCAttributeMappingRepository = dumWCAttributeMappingRepository;
    }

    private Collection<DumWCAttribExchange> attributeExchange(Collection<DumWCAttributeMapping> attributeMappings) {
        Collection<DumWCAttribExchange> attribExchanges = new ArrayList<>();
        attributeMappings.forEach(attributeMapping -> attribExchanges.add(DumWCAttribExchange.builder()
                .name(attributeMapping.getName())
                .className(attributeMapping.getClassName())
                .text(attributeMapping.getText())
                .build()));
        return attribExchanges;
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

            List<DumWCSource> dumWCSources = dumWCSourceRepository.findAll();

            File exportDir = new File(tmpDir.getAbsolutePath().concat(File.separator + folderName));
            File exportFile = new File(exportDir.getAbsolutePath().concat(File.separator + EXPORT_FILE));
            try {
                Files.createDirectories(exportDir.toPath());
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }

            ObjectMapper mapper = JsonMapper.builder()
                    .changeDefaultPropertyInclusion(incl -> JsonInclude.Value.construct(JsonInclude.Include.NON_NULL,
                            JsonInclude.Include.USE_DEFAULTS))
                    .build();
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(exportFile,
                        new DumWCExchange(dumWCSources.stream()
                                .map(dumWCSource -> DumWCSourceExchange.builder()
                                        .id(dumWCSource.getId())
                                        .title(dumWCSource.getTitle())
                                        .description(dumWCSource.getDescription())
                                        .url(dumWCSource.getUrl())
                                        .allowUrls(dumWCSource.getAllowUrls().stream().map(DumWCUrl::getUrl)
                                                .toList())
                                        .attributes(attributeExchange(dumWCSource.getAttributeMappings()))
                                        .locale(dumWCSource.getLocale())
                                        .password(dumWCSource.getPassword())
                                        .localeClass(dumWCSource.getLocaleClass())
                                        .turSNSites(dumWCSource.getTurSNSites())
                                        .username(dumWCSource.getUsername())
                                        .notAllowUrls(dumWCSource.getNotAllowUrls().stream().map(DumWCUrl::getUrl)
                                                .toList())
                                        .notAllowExtensions(dumWCSource.getNotAllowExtensions().stream()
                                                .map(DumWCFileExtension::getExtension).toList())
                                        .build())
                                .toList()));

                File zipFile = new File(tmpDir.getAbsolutePath().concat(File.separator + folderName + ".zip"));

                DumCommonsUtils.addFilesToZip(exportDir, zipFile);

                String strDate = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date());
                String zipFileName = "WC_" + strDate + ".zip";

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
            tmpFile = tmpDir.resolve("wc-import-" + java.util.UUID.randomUUID() + ".json").toFile();
            multipartFile.transferTo(tmpFile);
            DumSourceExchangeFile.normalize(tmpFile, "title");
            return action.apply(tmpFile);
        } catch (IOException e) {
            throw new DumSourceImportException("The uploaded file could not be read.", e);
        } finally {
            FileUtils.deleteQuietly(tmpFile);
        }
    }

    /** Whether the upload is a zip, told by its content and not its name (roadmap DU15). */
    private static boolean isZipUpload(MultipartFile multipartFile) {
        try (java.io.InputStream content = multipartFile.getInputStream()) {
            return DumSourceExchangeFile.isZip(content);
        } catch (IOException e) {
            throw new DumSourceImportException("The uploaded file could not be read.", e);
        }
    }

    /** Runs {@code action} on the upload's {@code export.json}, and cleans up what was extracted. */
    private <T> T withExportFile(MultipartFile multipartFile, java.util.function.Function<File, T> action) {
        if (!isZipUpload(multipartFile)) {
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
            DumSourceExchangeFile.normalize(exportFile, "title");
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
     * Reads {@code exportFile} and imports it in {@code mode}. When replacing, an unreadable file
     * refuses the whole import before anything is deleted.
     */
    public void importFromFile(File exportFile, DumSourceImportMode mode) {
        ObjectMapper mapper = new ObjectMapper();
        DumWCExchange dumWCExchange;
        try {
            dumWCExchange = mapper.readValue(exportFile, DumWCExchange.class);
        } catch (RuntimeException e) {
            if (mode != DumSourceImportMode.ADD) {
                throw new DumSourceImportException("The file could not be read as a source export.", e);
            }
            throw e;
        }
        if (mode == DumSourceImportMode.REPLACE) {
            replaceSources(dumWCExchange);
        } else if (mode == DumSourceImportMode.MERGE) {
            mergeSources(dumWCExchange);
        } else if (dumWCExchange.getSources() != null && !dumWCExchange.getSources().isEmpty()) {
            importWCSource(dumWCExchange);
        }
    }

    /**
     * The file becomes the whole set of web-crawler sources (roadmap DU9): every existing source is
     * deleted and the file's are created, inside this class's transaction, so a failure leaves the
     * instance as it was. A file holding a name that cannot be saved is refused before anything is
     * deleted. Ids are left to the generator; names are kept, and indexing history is keyed by name.
     */
    public DumSourceReplacePlan replaceSources(DumWCExchange exchange) {
        List<DumWCSourceExchange> sources = exchange.getSources() == null ? List.of()
                : List.copyOf(exchange.getSources());
        DumSourceReplacePlan plan = planReplace(sources);
        if (!plan.isApplicable()) {
            throw plan.refusal();
        }
        log.info("Replacing the web-crawler sources: create {}, overwrite {}, remove {}", plan.create(),
                plan.overwrite(), plan.remove());
        dumWCSourceRepository.deleteAll();
        dumWCSourceRepository.flush();
        sources.forEach(source -> source.setId(null));
        if (!sources.isEmpty()) {
            importWCSource(exchange);
        }
        return plan;
    }

    /**
     * The file updates the web-crawler sources it names and keeps the others (roadmap DU14): a source
     * whose title is in the file is deleted and recreated from it, a new title is created, and the
     * rest are left alone, inside this class's transaction. A file holding a title that cannot be
     * saved is refused before anything is deleted.
     */
    public DumSourceReplacePlan mergeSources(DumWCExchange exchange) {
        List<DumWCSourceExchange> sources = exchange.getSources() == null ? List.of()
                : List.copyOf(exchange.getSources());
        DumSourceReplacePlan plan = planReplace(sources).forMode(DumSourceImportMode.MERGE);
        if (!plan.isApplicable()) {
            throw plan.refusal();
        }
        log.info("Merging the web-crawler sources: create {}, overwrite {}", plan.create(), plan.overwrite());
        dumWCSourceRepository.deleteAll(dumWCSourceRepository.findAll().stream()
                .filter(source -> plan.overwrite().contains(source.getTitle())).toList());
        dumWCSourceRepository.flush();
        sources.forEach(source -> source.setId(null));
        if (!sources.isEmpty()) {
            importWCSource(exchange);
        }
        return plan;
    }

    /** What replacing with {@code exportFile} would do (roadmap DU10), without writing anything. */
    public DumSourceReplacePlan planFromFile(File exportFile) {
        return planFromFile(exportFile, DumSourceImportMode.REPLACE);
    }

    /** What importing {@code exportFile} in {@code mode} would do (roadmap DU14), without writing. */
    public DumSourceReplacePlan planFromFile(File exportFile, DumSourceImportMode mode) {
        DumWCExchange exchange;
        try {
            exchange = new ObjectMapper().readValue(exportFile, DumWCExchange.class);
        } catch (RuntimeException e) {
            throw new DumSourceImportException("The file could not be read as a source export.", e);
        }
        return planReplace(exchange.getSources() == null ? List.<DumWCSourceExchange>of() : List.copyOf(exchange.getSources()))
                .forMode(mode);
    }

    /** What replacing the web-crawler sources with {@code sources} would do, without writing anything. */
    public DumSourceReplacePlan planReplace(List<DumWCSourceExchange> sources) {
        return DumSourceReplacePlan.of(sources.stream().map(DumWCSourceExchange::getTitle).toList(),
                dumWCSourceRepository.findAll().stream().map(DumWCSource::getTitle).toList(), false);
    }

    public void importWCSource(DumWCExchange dumWCExchange) {
        for (DumWCSourceExchange dumWCSourceExchange : dumWCExchange.getSources()) {
            String id = dumWCSourceExchange.getId();
            if (id == null || id.isEmpty() || dumWCSourceRepository.findById(id).isEmpty()) {
                DumWCSource dumWCSource = DumWCSource.builder()
                        .title(dumWCSourceExchange.getTitle())
                        .description(dumWCSourceExchange.getDescription())
                        .url(dumWCSourceExchange.getUrl())
                        .username(dumWCSourceExchange.getUsername())
                        .password(dumWCSourceExchange.getPassword())
                        .turSNSites(dumWCSourceExchange.getTurSNSites())
                        .locale(dumWCSourceExchange.getLocale())
                        .localeClass(dumWCSourceExchange.getLocaleClass())
                        .build();

                dumWCSourceRepository.save(dumWCSource);

                dumWCSourceExchange.getStartingPoints()
                        .forEach(url -> dumWCStartingPointRepository.save(DumWCStartingPoint.builder()
                                .url(url)
                                .dumWCSource(dumWCSource)
                                .build()));
                dumWCSourceExchange.getAllowUrls().forEach(url -> dumWCAllowUrlRepository.save(DumWCAllowUrl.builder()
                        .url(url)
                        .dumWCSource(dumWCSource)
                        .build()));

                dumWCSourceExchange.getNotAllowUrls()
                        .forEach(url -> dumWCNotAllowUrlRepository.save(DumWCNotAllowUrl.builder()
                                .url(url)
                                .dumWCSource(dumWCSource)
                                .build()));

                dumWCSourceExchange.getNotAllowExtensions()
                        .forEach(extension -> dumWCFileExtensionRepository.save(DumWCFileExtension.builder()
                                .extension(extension)
                                .dumWCSource(dumWCSource)
                                .build()));

                dumWCSourceExchange.getAttributes()
                        .forEach(attribute -> dumWCAttributeMappingRepository.save(DumWCAttributeMapping.builder()
                                .name(attribute.getName())
                                .className(attribute.getClassName())
                                .text(attribute.getText())
                                .dumWCSource(dumWCSource)
                                .build()));
            }
        }
    }
}
