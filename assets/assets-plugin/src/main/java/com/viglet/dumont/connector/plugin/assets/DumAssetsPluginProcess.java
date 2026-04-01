package com.viglet.dumont.connector.plugin.assets;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.LocaleUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.plugin.assets.persistence.model.DumAssetsSource;
import com.viglet.dumont.connector.plugin.assets.persistence.repository.DumAssetsSourceRepository;
import com.viglet.turing.client.auth.TurServer;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.ocr.TurOcr;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.job.*;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DumAssetsPluginProcess {
    private static final String PROVIDER_NAME = "dumont-assets";
    private static final List<String> IMAGE_EXTENSIONS = List.of("pnm", "png", "jpg", "jpeg", "gif");

    private final DumAssetsSourceRepository repository;

    public DumAssetsPluginProcess(DumAssetsSourceRepository repository) {
        this.repository = repository;
    }

    @Async
    public void indexAllByIdAsync(String sourceId) {
        repository.findById(sourceId).ifPresent(this::start);
    }

    public void start(DumAssetsSource source) {
        log.info("Starting assets import for source: {}", source.getName());
        String sourceDir = source.getSourceDir();
        if (sourceDir == null || sourceDir.isBlank()) {
            log.error("Source directory is not configured for source: {}", source.getName());
            return;
        }

        Path startPath = Paths.get(sourceDir);
        if (!startPath.toFile().exists() || !startPath.toFile().isDirectory()) {
            log.error("Source directory does not exist: {}", sourceDir);
            return;
        }

        int chunk = source.getChunk() > 0 ? source.getChunk() : 100;
        String locale = source.getLocale() != null ? source.getLocale() : "en_US";
        String contentType = source.getContentType() != null ? source.getContentType() : "Static File";

        TurSNJobItems jobItems = new TurSNJobItems();
        int[] count = {0, 0}; // [currentChunk, total]

        try {
            Files.walkFileTree(startPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                            File file = path.toFile();
                            String ext = FilenameUtils.getExtension(file.getName()).toLowerCase();
                            if ("ds_store".equals(ext)) return FileVisitResult.CONTINUE;

                            TurSNJobItem item = createJobItem(source, file, locale, contentType);
                            if (item != null) {
                                jobItems.add(item);
                                count[0]++;
                                count[1]++;
                                if (count[0] >= chunk) {
                                    log.info("Importing batch, total so far: {}", count[1]);
                                    TurSNJobUtils.importItems(jobItems,
                                            new TurSNServer(URI.create("http://localhost:2700"), null,
                                                    new TurApiKeyCredentials(null)),
                                            source.isShowOutput());
                                    jobItems.clear();
                                    count[0] = 0;
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
            if (count[0] > 0) {
                log.info("Importing final batch, total: {}", count[1]);
                TurSNJobUtils.importItems(jobItems,
                        new TurSNServer(URI.create("http://localhost:2700"), null,
                                new TurApiKeyCredentials(null)),
                        source.isShowOutput());
            }
            log.info("Assets import completed for source: {}. Total files: {}", source.getName(), count[1]);
        } catch (IOException e) {
            log.error("Error walking file tree: {}", e.getMessage(), e);
        }
    }

    private TurSNJobItem createJobItem(DumAssetsSource source, File file, String locale, String contentType) {
        TurSNJobItem item = new TurSNJobItem(TurSNJobAction.CREATE,
                Collections.singletonList(source.getSite()),
                LocaleUtils.toLocale(locale));
        Map<String, Object> attributes = new HashMap<>();

        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(tz);

        String fileURL = file.getAbsolutePath();
        if (source.getPrefixFromReplace() != null && source.getPrefixToReplace() != null) {
            fileURL = fileURL.replace(source.getPrefixFromReplace(), source.getPrefixToReplace());
        }

        String id = source.isTypeInId() ? contentType + fileURL : fileURL;
        attributes.put("id", id);
        attributes.put("date", df.format(file.lastModified()));
        attributes.put("title", file.getName());
        attributes.put("type", contentType);
        attributes.put("sourceApps", PROVIDER_NAME);

        String ext = FilenameUtils.getExtension(file.getName()).toLowerCase();
        if (IMAGE_EXTENSIONS.contains(ext)) {
            attributes.put("image", fileURL);
        }
        if (source.getFileExtensionField() != null) {
            attributes.put(source.getFileExtensionField(), ext);
        }
        if (source.getFileSizeField() != null) {
            attributes.put(source.getFileSizeField(), file.length());
        }
        attributes.put("url", fileURL);

        item.setAttributes(attributes);
        return item;
    }
}
