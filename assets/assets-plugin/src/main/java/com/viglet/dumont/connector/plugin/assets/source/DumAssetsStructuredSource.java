/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
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

package com.viglet.dumont.connector.plugin.assets.source;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import com.viglet.dumont.connector.assets.commons.DumFileUtils;
import com.viglet.dumont.connector.assets.commons.DumTikaFileAttributes;
import com.viglet.dumont.connector.commons.spi.structured.DumEmbeddingRole;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance;
import com.viglet.dumont.connector.plugin.assets.DumAssetsPlugin;
import com.viglet.dumont.connector.plugin.assets.persistence.model.DumAssetsSource;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.field.TurSNFieldName;

import lombok.extern.slf4j.Slf4j;

/**
 * A {@link DumStructuredSource} that adapts a filesystem {@link DumAssetsSource} to the structured
 * SPI (roadmap <strong>D22</strong>).
 *
 * <p>
 * Instead of the legacy ad-hoc attribute map built inside a single directory-walk, the Assets
 * connector now honours the structured contract: the fixed field set is declared once as
 * {@link #schema() schema-as-code} ({@code title}, {@code url}, {@code date} plus the optional
 * {@code image}, {@code fileSize}, {@code fileExtension} and Tika {@code content} fields),
 * {@link #discover()} is the directory walk returning one id per file, and {@link #extract(String)}
 * resolves a single file into a typed {@link DumStructuredRecord}. Grounding, dry-run preview (D14),
 * field-coverage/drift (D15) and provenance (D16) are inherited from
 * {@link com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourcePlugin} — none of it
 * is reimplemented here.
 *
 * <p>
 * The {@code id} space is the file's absolute path on disk: {@code discover()} returns it and
 * {@code extract(id)} resolves it. The published document id is the (prefix-replaced) {@code url},
 * with the source's {@code typeInId} prefix applied — matching the legacy document identity. The
 * document {@code type} (content type) and {@code source_apps} are stamped by {@link DumAssetsPlugin}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public class DumAssetsStructuredSource implements DumStructuredSource {

    private static final String DEFAULT_LOCALE = "en_US";
    private static final String DEFAULT_CONTENT_TYPE = "Static File";
    private static final String DATE_FIELD = "date";
    private static final String DS_STORE = "ds_store";
    private static final List<String> IMAGE_EXTENSIONS =
            List.of("pnm", "png", "jpg", "jpeg", "gif");

    private final DumAssetsSource source;

    private DumStructuredSchema schema;

    public DumAssetsStructuredSource(DumAssetsSource source) {
        this.source = source;
    }

    @Override
    public String getSourceName() {
        return source.getName();
    }

    @Override
    public String getProviderName() {
        return DumAssetsPlugin.PROVIDER_NAME;
    }

    @Override
    public Collection<String> getSiteNames() {
        if (source.getTurSNSites() != null && !source.getTurSNSites().isEmpty()) {
            return source.getTurSNSites();
        }
        return StringUtils.isNotBlank(source.getSite()) ? List.of(source.getSite()) : List.of();
    }

    /** The Turing content type stamped as the document {@code type}; defaults to {@code Static File}. */
    public String getContentType() {
        return StringUtils.isNotBlank(source.getContentType()) ? source.getContentType()
                : DEFAULT_CONTENT_TYPE;
    }

    @Override
    public Locale getLocale() {
        String locale = source.getLocale();
        if (StringUtils.isBlank(locale)) {
            locale = DEFAULT_LOCALE;
        }
        String[] parts = locale.split("[_-]", 2);
        return parts.length == 2 ? Locale.of(parts[0], parts[1]) : Locale.of(parts[0]);
    }

    @Override
    public synchronized DumStructuredSchema schema() {
        if (schema == null) {
            DumStructuredSchema.Builder builder = DumStructuredSchema.builder()
                    .field(DumStructuredField.builder(TurSNFieldName.TITLE).type(TurSEFieldType.TEXT)
                            .mandatory(true).embeddingRole(DumEmbeddingRole.TITLE)
                            .description("File name").build())
                    .field(DumStructuredField.builder(TurSNFieldName.URL).type(TurSEFieldType.STRING)
                            .mandatory(true).description("Absolute file URL/path").build())
                    .field(DumStructuredField.builder(DATE_FIELD).type(TurSEFieldType.DATE)
                            .description("Last-modified date").build())
                    .field(DumStructuredField.builder(TurSNFieldName.IMAGE).type(TurSEFieldType.STRING)
                            .description("File URL when the file is an image").build());
            if (StringUtils.isNotBlank(source.getFileSizeField())) {
                builder.field(DumStructuredField.builder(source.getFileSizeField())
                        .type(TurSEFieldType.LONG).description("File size in bytes").build());
            }
            if (StringUtils.isNotBlank(source.getFileExtensionField())) {
                builder.field(DumStructuredField.builder(source.getFileExtensionField())
                        .type(TurSEFieldType.STRING).description("File extension").build());
            }
            if (StringUtils.isNotBlank(source.getContentField())) {
                builder.field(DumStructuredField.builder(source.getContentField())
                        .type(TurSEFieldType.TEXT).embeddingRole(DumEmbeddingRole.BODY)
                        .description("Tika-extracted file content").build());
            }
            schema = builder.build();
        }
        return schema;
    }

    @Override
    public List<String> discover() {
        List<String> ids = new ArrayList<>();
        String sourceDir = source.getSourceDir();
        if (StringUtils.isBlank(sourceDir)) {
            log.error("Assets source '{}' has no source directory configured", source.getName());
            return ids;
        }
        Path startPath = Paths.get(sourceDir);
        File start = startPath.toFile();
        if (!start.exists() || !start.isDirectory()) {
            log.error("Assets source '{}' directory does not exist: {}", source.getName(), sourceDir);
            return ids;
        }
        try {
            Files.walkFileTree(startPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                            File file = path.toFile();
                            String ext = FilenameUtils.getExtension(file.getName()).toLowerCase();
                            if (!DS_STORE.equals(ext)) {
                                ids.add(file.getAbsolutePath());
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            log.error("Assets source '{}' failed to walk '{}': {}", source.getName(), sourceDir,
                    e.getMessage(), e);
        }
        log.info("Assets source '{}' discovered {} file(s)", source.getName(), ids.size());
        return ids;
    }

    @Override
    public Optional<DumStructuredRecord> extract(String contentId) {
        File file = new File(contentId);
        if (!file.exists() || file.isDirectory()) {
            return Optional.empty();
        }
        String ext = FilenameUtils.getExtension(file.getName()).toLowerCase();
        String fileURL = applyPrefix(file.getAbsolutePath());
        String documentId = source.isTypeInId() ? getContentType() + fileURL : fileURL;

        DumStructuredRecord.Builder rec = DumStructuredRecord.builder(documentId, schema());
        rec.set(TurSNFieldName.TITLE, file.getName(), DumFieldProvenance.filesystem("name"));
        rec.set(TurSNFieldName.URL, fileURL, DumFieldProvenance.filesystem("path"));
        rec.set(DATE_FIELD, formatDate(file.lastModified()),
                DumFieldProvenance.filesystem("lastModified"));
        if (IMAGE_EXTENSIONS.contains(ext)) {
            rec.set(TurSNFieldName.IMAGE, fileURL, DumFieldProvenance.filesystem("path"));
        }
        if (StringUtils.isNotBlank(source.getFileSizeField())) {
            rec.set(source.getFileSizeField(), file.length(),
                    DumFieldProvenance.filesystem("size"));
        }
        if (StringUtils.isNotBlank(source.getFileExtensionField())) {
            rec.set(source.getFileExtensionField(), ext,
                    DumFieldProvenance.filesystem("extension"));
        }
        if (StringUtils.isNotBlank(source.getContentField())) {
            extractContent(file).ifPresent(content -> rec.set(source.getContentField(), content,
                    DumFieldProvenance.filesystem("tika:content")));
        }
        return Optional.of(rec.build());
    }

    /** Tika-extract the file body for the optional {@code content} field; grounding drops a blank. */
    private Optional<String> extractContent(File file) {
        try {
            DumTikaFileAttributes attributes = DumFileUtils.readFile(file);
            return Optional.ofNullable(attributes).map(DumTikaFileAttributes::getContent)
                    .map(String::strip).filter(StringUtils::isNotBlank);
        } catch (RuntimeException e) {
            log.warn("Assets source '{}' failed to extract content from '{}': {}", source.getName(),
                    file.getAbsolutePath(), e.getMessage());
            return Optional.empty();
        }
    }

    private String applyPrefix(String absolutePath) {
        if (source.getPrefixFromReplace() != null && source.getPrefixToReplace() != null) {
            return absolutePath.replace(source.getPrefixFromReplace(), source.getPrefixToReplace());
        }
        return absolutePath;
    }

    private static String formatDate(long epochMillis) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(epochMillis);
    }
}
