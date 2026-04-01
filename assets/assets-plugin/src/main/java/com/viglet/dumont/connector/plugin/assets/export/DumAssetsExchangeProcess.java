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
        importFromFile(new File(extractFolder, EXPORT_FILE));
        try {
            FileUtils.deleteDirectory(extractFolder);
            if (parentExtractFolder != null) FileUtils.deleteDirectory(parentExtractFolder);
        } catch (IOException e) { log.error(e.getMessage(), e); }
    }

    public void importFromFile(File exportFile) {
        log.info("Importing {} file", exportFile);
        try {
            ObjectMapper mapper = JsonMapper.builder()
                    .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false).build();
            DumAssetsExchange exchange = mapper.readValue(exportFile, DumAssetsExchange.class);
            if (exchange.getSources() != null && !exchange.getSources().isEmpty()) {
                exchange.getSources().stream()
                        .filter(s -> s.getId() == null || s.getId().isEmpty() || repository.findById(s.getId()).isEmpty())
                        .forEach(this::importSource);
            }
        } catch (StreamReadException | DatabindException | JacksonIOException e) { log.error(e.getMessage(), e); }
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
