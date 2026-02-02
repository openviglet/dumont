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
            DumAemExchange dumAemExchange = mapper.readValue(exportFile, DumAemExchange.class);
            if (hasSource(dumAemExchange)) {
                importAemSource(dumAemExchange);
            }
        } catch (StreamReadException | DatabindException | JacksonIOException e) {
            log.error(e.getMessage(), e);
        }

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
                .filter(dumAemSourceExchange -> dumAemSourceRepository.findById(dumAemSourceExchange.getId()).isEmpty())
                .forEach(dumAemSourceExchange -> {
                    DumAemSource source = setSource(dumAemSourceExchange);
                    setFacetNames(dumAemSourceExchange, source);
                    setModels(dumAemSourceExchange, source);
                });
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
