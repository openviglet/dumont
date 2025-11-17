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
import com.fasterxml.jackson.databind.ObjectMapper;
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

            // Object to JSON in file
            ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(exportFile,
                        new DumWCExchange(dumWCSources.stream()
                                .map(dumWCSource -> DumWCSourceExchange.builder()
                                        .id(dumWCSource.getId())
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
        ObjectMapper mapper = new ObjectMapper();
        try {
            DumWCExchange dumWCExchange = mapper.readValue(exportFile, DumWCExchange.class);
            if (dumWCExchange.getSources() != null && !dumWCExchange.getSources().isEmpty()) {
                importWCSource(dumWCExchange);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public void importWCSource(DumWCExchange dumWCExchange) {
        for (DumWCSourceExchange dumWCSourceExchange : dumWCExchange.getSources()) {
            if (dumWCSourceRepository.findById(dumWCSourceExchange.getId()).isEmpty()) {
                DumWCSource dumWCSource = DumWCSource.builder()
                        // .id(dumWCSourceExchange.getId())
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
