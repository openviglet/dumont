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

package com.viglet.dumont.connector.plugin.db;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.LocaleUtils;
import org.jsoup.Jsoup;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.viglet.dumont.commons.cache.DumCustomClassCache;
import com.viglet.dumont.connector.db.ext.DumDbExtCustomImpl;
import com.viglet.dumont.connector.assets.commons.DumFileUtils;
import com.viglet.dumont.connector.assets.commons.DumTikaFileAttributes;
import com.viglet.dumont.connector.plugin.db.persistence.model.DumDbSource;
import com.viglet.dumont.connector.plugin.db.persistence.repository.DumDbSourceRepository;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DumDbPluginProcess {

    private static final long MEGA_BYTE = 1024L * 1024L;
    private static final String PROVIDER_NAME = "dumont-jdbc";
    private static final String SQL_TIMESTAMP_CLASS = "java.sql.Timestamp";
    private static final String INTEGER_CLASS = "java.lang.Integer";
    private static final String UTC = "UTC";
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String FILE_PROTOCOL = "file://";

    private final DumDbSourceRepository dumDbSourceRepository;

    public DumDbPluginProcess(DumDbSourceRepository dumDbSourceRepository) {
        this.dumDbSourceRepository = dumDbSourceRepository;
    }

    @Async
    public void indexAllByIdAsync(String sourceId) {
        dumDbSourceRepository.findById(sourceId).ifPresent(this::start);
    }

    public void start(DumDbSource source) {
        log.info("Starting DB import for source: {}", source.getName());
        log.info("driver: {}", source.getDriver());
        log.info("url: {}", source.getUrl());
        log.info("query: {}", getFormattedQuery(source));

        if (!loadJDBCDriver(source.getDriver())) {
            return;
        }

        TurSNServer turSNServer = createTurSNServer(source);
        DumDbExtCustomImpl customImpl = instantiateCustomClass(source.getCustomClassName());

        if (source.isDeindexBeforeImporting()) {
            turSNServer.deleteItemsByType(source.getContentType());
        }

        log.info("Executing query...");
        try (Connection conn = DriverManager.getConnection(source.getUrl(), source.getDbUsername(),
                source.getDbPassword());
                Statement stmt = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                        ResultSet.CONCUR_READ_ONLY);
                ResultSet rs = stmt.executeQuery(getFormattedQuery(source))) {

            int totalRows = 0;
            if (rs.last()) {
                totalRows = rs.getRow();
                rs.beforeFirst();
            }

            int chunk = source.getChunk() > 0 ? source.getChunk() : 100;
            TurSNJobItems jobItems = new TurSNJobItems();
            int currentCount = 0;
            int firstItemPosition = 1;

            while (rs.next()) {
                jobItems.add(createJobItem(source, customImpl, conn, rs));
                currentCount++;

                if (currentCount >= chunk) {
                    log.info("Importing {} to {} of {} items", firstItemPosition,
                            firstItemPosition + currentCount - 1, totalRows);
                    turSNServer.importItems(jobItems, source.isShowOutput());
                    firstItemPosition += currentCount;
                    currentCount = 0;
                    jobItems = new TurSNJobItems();
                }
            }

            if (currentCount > 0) {
                log.info("Importing {} to {} of {} items", firstItemPosition,
                        firstItemPosition + currentCount - 1, totalRows);
                turSNServer.importItems(jobItems, source.isShowOutput());
            }

            log.info("DB import completed for source: {}", source.getName());
        } catch (SQLException e) {
            log.error("SQL error during import: {}", e.getMessage(), e);
        }
    }

    private String getFormattedQuery(DumDbSource source) {
        String query = source.getQuery();
        if (query != null && query.startsWith(FILE_PROTOCOL)) {
            try {
                return Files.readString(Paths.get(query.replace(FILE_PROTOCOL, "")));
            } catch (IOException e) {
                log.error("Failed to read query file: {}", e.getMessage(), e);
            }
        }
        return query;
    }

    private boolean loadJDBCDriver(String driver) {
        try {
            Class.forName(driver);
            return true;
        } catch (ClassNotFoundException e) {
            log.error("JDBC driver not found: {}", e.getMessage(), e);
            return false;
        }
    }

    private TurSNServer createTurSNServer(DumDbSource source) {
        String site = source.getSite();
        String locale = source.getLocale() != null ? source.getLocale() : "en_US";
        TurSNServer turSNServer = new TurSNServer(URI.create("http://localhost:2700"),
                site,
                LocaleUtils.toLocale(locale),
                new TurApiKeyCredentials(null));
        turSNServer.setProviderName(PROVIDER_NAME);
        return turSNServer;
    }

    private DumDbExtCustomImpl instantiateCustomClass(String className) {
        return Optional.ofNullable(className)
                .map(cn -> (DumDbExtCustomImpl) DumCustomClassCache.getCustomClassMap(cn).orElse(null))
                .orElse(null);
    }

    private TurSNJobItem createJobItem(DumDbSource source, DumDbExtCustomImpl customImpl,
            Connection conn, ResultSet rs) throws SQLException {
        Map<String, Object> attributes = new HashMap<>();
        String contentType = source.getContentType() != null ? source.getContentType() : "CONTENT_TYPE";
        attributes.put("type", contentType);
        attributes.put("sourceApps", PROVIDER_NAME);

        addDBFieldsAsAttributes(source, rs, attributes);
        addFileAttributes(source, attributes);
        attributes = modifyAttributesByCustomClass(customImpl, source.getCustomClassName(), conn, attributes);
        addMultiValuedAttributes(source, attributes);

        TurSNJobItem turSNJobItem = new TurSNJobItem(TurSNJobAction.CREATE,
                Collections.singletonList(source.getSite()),
                LocaleUtils.toLocale(source.getLocale() != null ? source.getLocale() : "en_US"));
        turSNJobItem.setAttributes(attributes);
        return turSNJobItem;
    }

    private void addDBFieldsAsAttributes(DumDbSource source, ResultSet rs,
            Map<String, Object> attributes) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        String htmlFields = source.getRemoveHtmlTagsFields() != null ? source.getRemoveHtmlTagsFields() : "";
        boolean includeTypeInId = source.isTypeInId();
        String contentType = source.getContentType() != null ? source.getContentType() : "CONTENT_TYPE";

        for (int c = 1; c <= metaData.getColumnCount(); c++) {
            String columnName = metaData.getColumnLabel(c);
            String className = metaData.getColumnClassName(c);

            if (INTEGER_CLASS.equals(className)) {
                int intValue = rs.getInt(c);
                attributes.put(columnName, formatValue(columnName, Integer.toString(intValue),
                        htmlFields, includeTypeInId, contentType));
            } else if (SQL_TIMESTAMP_CLASS.equals(className)) {
                DateFormat df = new SimpleDateFormat(DATE_FORMAT);
                df.setTimeZone(TimeZone.getTimeZone(UTC));
                attributes.put(columnName, formatValue(columnName, df.format(rs.getDate(c)),
                        htmlFields, includeTypeInId, contentType));
            } else {
                String strValue = rs.getString(c);
                attributes.put(columnName, formatValue(columnName, strValue,
                        htmlFields, includeTypeInId, contentType));
            }
        }
    }

    private String formatValue(String fieldName, String value, String htmlFields,
            boolean includeTypeInId, String contentType) {
        if (value == null) return null;

        if ("id".equalsIgnoreCase(fieldName) && includeTypeInId) {
            value = contentType + "_" + value;
        }

        if (htmlFields != null && !htmlFields.isEmpty()) {
            for (String htmlField : htmlFields.split(",")) {
                if (fieldName.equalsIgnoreCase(htmlField.trim())) {
                    return Jsoup.parse(value).text();
                }
            }
        }
        return value;
    }

    private void addFileAttributes(DumDbSource source, Map<String, Object> attributes) {
        String filePathField = source.getFilePathField();
        if (filePathField != null && attributes.containsKey(filePathField)) {
            DumTikaFileAttributes tikaAttrs = DumFileUtils.readFile((String) attributes.get(filePathField));
            if (tikaAttrs != null) {
                long maxBytes = source.getMaxContentMegaByteSize() * MEGA_BYTE;

                if (source.getFileSizeField() != null && tikaAttrs.getFile() != null) {
                    attributes.put(source.getFileSizeField(), tikaAttrs.getFile().length());
                }

                if (source.getFileContentField() != null) {
                    String content = tikaAttrs.getContent();
                    if (content.getBytes().length <= maxBytes) {
                        attributes.put(source.getFileContentField(), content);
                    } else {
                        attributes.put(source.getFileContentField(),
                                content.substring(0, Math.toIntExact(maxBytes)));
                        log.debug("File size greater than {}, truncating content",
                                FileUtils.byteCountToDisplaySize(maxBytes));
                    }
                }
            }
        }
    }

    private Map<String, Object> modifyAttributesByCustomClass(DumDbExtCustomImpl customImpl,
            String className, Connection conn, Map<String, Object> attributes) {
        if (className != null && customImpl != null) {
            return customImpl.run(conn, attributes);
        }
        return attributes;
    }

    private void addMultiValuedAttributes(DumDbSource source, Map<String, Object> attributes) {
        String mvFields = source.getMultiValuedFields();
        String mvSeparator = source.getMultiValuedSeparator() != null ? source.getMultiValuedSeparator() : ",";

        if (mvFields == null || mvFields.isEmpty()) return;

        attributes.forEach((attributeName, value) -> {
            String attributeValue = String.valueOf(value);
            Arrays.stream(mvFields.toLowerCase().split(","))
                    .filter(field -> attributeName.equalsIgnoreCase(field.trim()) && attributeValue != null)
                    .map(field -> Arrays.asList(attributeValue.split(mvSeparator)))
                    .forEach(multiValueList -> attributes.put(attributeName, multiValueList));
        });
    }
}
