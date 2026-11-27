/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.aem.export;

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
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.viglet.dumont.connector.commons.DumSourceNames;
import com.viglet.dumont.commons.utils.DumCommonsUtils;
import com.viglet.dumont.connector.plugin.aem.export.bean.DumAemAttribExchange;
import com.viglet.dumont.connector.plugin.aem.export.bean.DumAemExchange;
import com.viglet.dumont.connector.plugin.aem.export.bean.DumAemModelExchange;
import com.viglet.dumont.connector.plugin.aem.export.bean.DumAemSourceExchange;
import com.viglet.dumont.connector.plugin.aem.export.bean.DumAemTargetAttrExchange;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemAttributeSpecification;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemPluginModel;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSourceAttribute;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSourceLocalePath;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemTargetAttribute;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemAttributeSpecificationRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemPluginModelRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemSourceAttributeRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemSourceLocalePathRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemSourceRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemTargetAttributeRepository;
import com.viglet.dumont.spring.utils.DumSpringUtils;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.exc.JacksonIOException;
import com.viglet.dumont.connector.commons.exchange.DumSourceImportException;
import com.viglet.dumont.connector.commons.exchange.DumSourceExchangeFile;
import com.viglet.dumont.connector.commons.exchange.DumSourceImportMode;
import com.viglet.dumont.connector.commons.exchange.DumSourceReplacePlan;

import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * @author Alexandre Oliveira
 * @since 0.3.9
 */
@Slf4j
@Component
@Transactional
public class DumAemExchangeProcess {
    private static final String EXPORT_FILE = "export.json";
    private final DumAemSourceRepository dumAemSourceRepository;
    private final DumAemAttributeSpecificationRepository dumAemAttributeSpecificationRepository;
    private final DumAemPluginModelRepository dumAemModelRepository;
    private final DumAemTargetAttributeRepository dumAemTargetAttributeRepository;
    private final DumAemSourceAttributeRepository dumAemSourceAttributeRepository;
    private final DumAemSourceLocalePathRepository dumAemSourceLocalePathRepository;

    public DumAemExchangeProcess(DumAemSourceRepository dumAemSourceRepository,
            DumAemAttributeSpecificationRepository dumAemAttributeSpecificationRepository,
            DumAemPluginModelRepository dumAemModelRepository,
            DumAemTargetAttributeRepository dumAemTargetAttributeRepository,
            DumAemSourceAttributeRepository dumAemSourceAttributeRepository,
            DumAemSourceLocalePathRepository dumAemSourceLocalePathRepository) {
        this.dumAemSourceRepository = dumAemSourceRepository;
        this.dumAemAttributeSpecificationRepository = dumAemAttributeSpecificationRepository;
        this.dumAemModelRepository = dumAemModelRepository;
        this.dumAemTargetAttributeRepository = dumAemTargetAttributeRepository;
        this.dumAemSourceAttributeRepository = dumAemSourceAttributeRepository;
        this.dumAemSourceLocalePathRepository = dumAemSourceLocalePathRepository;
    }

    private Collection<DumAemAttribExchange> attributeExchange(
            Collection<DumAemAttributeSpecification> attributeSpecifications) {
        Collection<DumAemAttribExchange> attribExchanges = new ArrayList<>();
        attributeSpecifications.forEach(attributeSpecification -> attribExchanges.add(DumAemAttribExchange.builder()
                .name(attributeSpecification.getName())
                .className(attributeSpecification.getClassName())
                .text(attributeSpecification.getText())
                .type(attributeSpecification.getType())
                .mandatory(attributeSpecification.isMandatory())
                .multiValued(attributeSpecification.isMultiValued())
                .description(attributeSpecification.getDescription())
                .facet(attributeSpecification.isFacet())
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
            List<DumAemSource> dumAemSources = dumAemSourceRepository.findAll();
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
                        new DumAemExchange(dumAemSources.stream()
                                .map(dumAemSource -> DumAemSourceExchange.builder()
                                        .id(dumAemSource.getId())
                                        .endpoint(dumAemSource.getEndpoint())
                                        .attributes(attributeExchange(dumAemSource.getAttributeSpecifications()))
                                        .defaultLocale(dumAemSource.getDefaultLocale())
                                        .localeClass(dumAemSource.getLocaleClass())
                                        .indexConditionClass(dumAemSource.getIndexConditionClass())
                                        .authorSNSite(dumAemSource.getAuthorSNSite())
                                        .publishSNSite(dumAemSource.getPublishSNSite())
                                        .author(dumAemSource.isAuthor())
                                        .publish(dumAemSource.isPublish())
                                        .name(dumAemSource.getName())
                                        .build())
                                .toList()));
                File zipFile = new File(tmpDir.getAbsolutePath().concat(File.separator + folderName + ".zip"));
                DumCommonsUtils.addFilesToZip(exportDir, zipFile);
                String strDate = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date());
                String zipFileName = "Aem_" + strDate + ".zip";
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

    /** An uploaded zip or plain {@code .json}, imported in {@code mode} (roadmap DU9). */
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
            DumSourceExchangeFile.normalize(exportFile, "name");
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

    /**
     * A plain {@code .json} upload: the per-source file the console's Export writes, or an
     * {@code export.json} taken out of its zip. It is read through the same path as the zip.
     */
    private <T> T withJsonUpload(MultipartFile multipartFile, java.util.function.Function<File, T> action) {
        File tmpFile = null;
        try {
            java.nio.file.Path tmpDir = Paths.get(System.getProperty("user.dir"), "store", "tmp");
            Files.createDirectories(tmpDir);
            tmpFile = tmpDir.resolve("aem-import-" + UUID.randomUUID() + ".json").toFile();
            multipartFile.transferTo(tmpFile);
            DumSourceExchangeFile.normalize(tmpFile, "name");
            return action.apply(tmpFile);
        } catch (IOException e) {
            throw new DumSourceImportException("The uploaded file could not be read.", e);
        } finally {
            FileUtils.deleteQuietly(tmpFile);
        }
    }

    public void importFromFile(File exportFile) {
        importFromFile(exportFile, DumSourceImportMode.ADD);
    }

    /**
     * Reads {@code exportFile} and imports it in {@code mode}. An unreadable file is logged and
     * skipped when adding, and refuses the whole import when replacing or merging, before anything
     * is deleted.
     */
    public void importFromFile(File exportFile, DumSourceImportMode mode) {
        log.info("Importing {} file ({})", exportFile, mode);
        DumAemExchange dumAemExchange;
        try {
            ObjectMapper mapper = JsonMapper.builder()
                    .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
                    .build();
            dumAemExchange = mapper.readValue(exportFile, DumAemExchange.class);
        } catch (StreamReadException | DatabindException | JacksonIOException e) {
            if (mode != DumSourceImportMode.ADD) {
                throw new DumSourceImportException("The file could not be read as a source export.", e);
            }
            log.error(e.getMessage(), e);
            return;
        }
        if (mode == DumSourceImportMode.REPLACE) {
            replaceAemSources(dumAemExchange);
        } else if (mode == DumSourceImportMode.MERGE) {
            mergeAemSources(dumAemExchange);
        } else if (hasSource(dumAemExchange)) {
            importAemSource(dumAemExchange);
        }
    }

    /**
     * The file updates the AEM sources it names and keeps the others (roadmap DU14): a source whose
     * name is in the file is deleted and recreated from it, a new name is created, and the rest are
     * left alone, inside this class's transaction. A file holding a name that cannot be saved is
     * refused before anything is deleted.
     */
    public DumSourceReplacePlan mergeAemSources(DumAemExchange dumAemExchange) {
        List<DumAemSourceExchange> sources = dumAemExchange.getSources() == null ? List.of()
                : List.copyOf(dumAemExchange.getSources());
        DumSourceReplacePlan plan = planReplace(sources).forMode(DumSourceImportMode.MERGE);
        if (!plan.isApplicable()) {
            throw plan.refusal();
        }
        log.info("Merging the AEM sources: create {}, overwrite {}", plan.create(), plan.overwrite());
        dumAemSourceRepository.deleteAll(dumAemSourceRepository.findAll().stream()
                .filter(source -> plan.overwrite().contains(source.getName())).toList());
        dumAemSourceRepository.flush();
        sources.forEach(source -> source.setId(null));
        if (!sources.isEmpty()) {
            importAemSource(dumAemExchange);
        }
        return plan;
    }

    /**
     * The file becomes the whole set of AEM sources (roadmap DU9): every existing source is
     * deleted and the file's are created, inside this class's transaction, so a failure leaves the
     * instance as it was. A file holding a name that cannot be saved is refused before anything is
     * deleted. Ids are left to the generator; names are kept, and indexing history is keyed by name.
     */
    public DumSourceReplacePlan replaceAemSources(DumAemExchange dumAemExchange) {
        List<DumAemSourceExchange> sources = dumAemExchange.getSources() == null ? List.of()
                : List.copyOf(dumAemExchange.getSources());
        DumSourceReplacePlan plan = planReplace(sources);
        if (!plan.isApplicable()) {
            throw plan.refusal();
        }
        log.info("Replacing the AEM sources: create {}, overwrite {}, remove {}", plan.create(),
                plan.overwrite(), plan.remove());
        dumAemSourceRepository.deleteAll();
        dumAemSourceRepository.flush();
        sources.forEach(source -> source.setId(null));
        if (!sources.isEmpty()) {
            importAemSource(dumAemExchange);
        }
        return plan;
    }

    /** What replacing with {@code exportFile} would do (roadmap DU10), without writing anything. */
    public DumSourceReplacePlan planFromFile(File exportFile) {
        return planFromFile(exportFile, DumSourceImportMode.REPLACE);
    }

    /** What importing {@code exportFile} in {@code mode} would do (roadmap DU14), without writing. */
    public DumSourceReplacePlan planFromFile(File exportFile, DumSourceImportMode mode) {
        DumAemExchange exchange;
        try {
            exchange = JsonMapper.builder()
                    .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false).build().readValue(exportFile, DumAemExchange.class);
        } catch (RuntimeException e) {
            throw new DumSourceImportException("The file could not be read as a source export.", e);
        }
        return planReplace(exchange.getSources() == null ? List.<DumAemSourceExchange>of() : List.copyOf(exchange.getSources()))
                .forMode(mode);
    }

    /** What replacing the AEM sources with {@code sources} would do, without writing anything. */
    public DumSourceReplacePlan planReplace(List<DumAemSourceExchange> sources) {
        return DumSourceReplacePlan.of(sources.stream().map(DumAemSourceExchange::getName).toList(),
                dumAemSourceRepository.findAll().stream().map(DumAemSource::getName).toList(), true);
    }

    private static boolean hasSource(DumAemExchange dumAemExchange) {
        return dumAemExchange.getSources() != null && !dumAemExchange.getSources().isEmpty();
    }

    public void importAemSource(DumAemExchange dumAemExchange) {
        setSources(dumAemExchange);
    }

    private void setSources(DumAemExchange dumAemExchange) {
        dumAemExchange.getSources()
                .stream()
                .filter(this::isNewSource)
                .forEach(dumAemSourceExchange -> {
                    DumAemSource source = setSource(dumAemSourceExchange);
                    setFacetNames(dumAemSourceExchange, source);
                    setModels(dumAemSourceExchange, source);
                });
    }

    /**
     * Whether an exported source still has to be created here (roadmap <strong>DU1</strong>). A
     * file the console's per-source Export wrote carries no id, and Spring Data refuses a null id,
     * so the id is looked up only when present; a source without one gets a generated id, and one
     * whose name already exists is skipped, so importing the same file twice creates nothing twice.
     */
    private boolean isNewSource(DumAemSourceExchange exchange) {
        if (!hasValidName(exchange.getName())) {
            return false;
        }
        if (exchange.getId() != null && exchange.getId().isBlank()) {
            exchange.setId(null);
        }
        if (exchange.getId() != null && dumAemSourceRepository.findById(exchange.getId()).isPresent()) {
            log.info("AEM source {} ({}) already exists, skipping it", exchange.getName(), exchange.getId());
            return false;
        }
        if (exchange.getName() != null && dumAemSourceRepository.findByName(exchange.getName()).isPresent()) {
            log.info("An AEM source named {} already exists, skipping it", exchange.getName());
            return false;
        }
        return true;
    }

    private boolean hasValidName(String name) {
        if (DumSourceNames.isValid(name)) {
            return true;
        }
        log.warn("Skipping the source {}: {}", name, DumSourceNames.RULE);
        return false;
    }

    private void setModels(DumAemSourceExchange dumAemSourceExchange, DumAemSource dumAemSource) {
        dumAemSourceExchange.getModels().forEach(model -> setTargetAttributes(model, setModel(model, dumAemSource)));
    }

    private @NotNull DumAemPluginModel setModel(DumAemModelExchange model, DumAemSource dumAemSource) {
        return dumAemModelRepository.save(DumAemPluginModel.builder()
                .type(model.getType())
                .className(model.getClassName())
                .dumAemSource(dumAemSource)
                .build());
    }

    private void setTargetAttributes(DumAemModelExchange model, DumAemPluginModel dumAemModel) {
        model.getTargetAttrs()
                .forEach(targetAttr -> setSourceAttributes(targetAttr, setTargetAttribute(dumAemModel, targetAttr)));
    }

    private @NotNull DumAemTargetAttribute setTargetAttribute(DumAemPluginModel dumAemModel,
            DumAemTargetAttrExchange targetAttr) {
        return dumAemTargetAttributeRepository.save(DumAemTargetAttribute.builder()
                .name(targetAttr.getName())
                .dumAemModel(dumAemModel)
                .build());
    }

    private void setSourceAttributes(DumAemTargetAttrExchange targetAttr, DumAemTargetAttribute dumAemTargetAttribute) {
        targetAttr.getSourceAttrs()
                .forEach(sourceAttr -> dumAemSourceAttributeRepository.save(DumAemSourceAttribute.builder()
                        .name(sourceAttr.getName())
                        .className(sourceAttr.getClassName())
                        .text(sourceAttr.getText())
                        .dumAemTargetAttribute(dumAemTargetAttribute)
                        .build()));
    }

    private void setFacetNames(DumAemSourceExchange dumAemSourceExchange, DumAemSource dumAemSource) {
        dumAemSourceExchange.getAttributes()
                .forEach(attribute -> setFacetName(attribute, setAttributeMapping(dumAemSource, attribute)));
    }

    private void setFacetName(DumAemAttribExchange attribute,
            DumAemAttributeSpecification dumAemAttributeSpecification) {
        dumAemAttributeSpecification.setFacetNames(attribute.getFacetName());
    }

    private DumAemAttributeSpecification setAttributeMapping(DumAemSource dumAemSource,
            DumAemAttribExchange attribute) {
        return dumAemAttributeSpecificationRepository.save(DumAemAttributeSpecification.builder()
                .name(attribute.getName())
                .className(attribute.getClassName())
                .text(attribute.getText())
                .type(attribute.getType())
                .mandatory(attribute.isMandatory())
                .multiValued(attribute.isMultiValued())
                .description(attribute.getDescription())
                .dumAemSource(dumAemSource)
                .build());
    }

    private @NotNull DumAemSource setSource(DumAemSourceExchange dumAemSourceExchange) {
        DumAemSource dumAemSource = DumAemSource.builder()
                .endpoint(dumAemSourceExchange.getEndpoint())
                .authorSNSite(dumAemSourceExchange.getAuthorSNSite())
                .publishSNSite(dumAemSourceExchange.getPublishSNSite())
                .defaultLocale(dumAemSourceExchange.getDefaultLocale())
                .localeClass(dumAemSourceExchange.getLocaleClass())
                .deltaClass(dumAemSourceExchange.getDeltaClass())
                .dependencyClass(dumAemSourceExchange.getDependencyClass())
                .indexConditionClass(dumAemSourceExchange.getIndexConditionClass())
                .authorURLPrefix(dumAemSourceExchange.getAuthorURLPrefix())
                .publishURLPrefix(dumAemSourceExchange.getPublishURLPrefix())
                .oncePattern(dumAemSourceExchange.getOncePattern())
                .username(dumAemSourceExchange.getUsername())
                .password(dumAemSourceExchange.getPassword())
                .rootPath(dumAemSourceExchange.getRootPath())
                .author(dumAemSourceExchange.isAuthor())
                .publish(dumAemSourceExchange.isPublish())
                .contentType(dumAemSourceExchange.getContentType())
                .name(dumAemSourceExchange.getName())
                .build();
        dumAemSourceRepository.save(dumAemSource);
        dumAemSourceExchange.getLocalePaths()
                .forEach(localePath -> dumAemSourceLocalePathRepository.save(DumAemSourceLocalePath.builder()
                        .locale(localePath.getLocale())
                        .path(localePath.getPath())
                        .dumAemSource(dumAemSource).build()));
        return dumAemSource;
    }
}
