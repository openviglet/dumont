/*
 * Copyright (C) 2016-2026 the original author or authors.
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

package com.viglet.dumont.connector.plugin.db.source;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import com.viglet.dumont.commons.cache.DumCustomClassCache;
import com.viglet.dumont.connector.assets.commons.DumFileUtils;
import com.viglet.dumont.connector.assets.commons.DumTikaFileAttributes;
import com.viglet.dumont.connector.db.ext.DumDbExtCustomImpl;
import com.viglet.dumont.connector.plugin.db.persistence.model.DumDbSource;

import lombok.extern.slf4j.Slf4j;

/**
 * Maps a single JDBC result-set row to a raw attribute map for the structured-source SPI
 * (roadmap <strong>D20</strong>).
 *
 * <p>
 * This is the per-row mapping the legacy all-rows batch loop used, factored out so the
 * {@link DumDbStructuredSource} can reuse it for its {@code extract(id)} contract: column values
 * (with JDBC type detection and HTML stripping), Tika file content, and the optional custom-class
 * hook. Identity, the declared-schema projection and multi-valued splitting are the source's
 * concern, so this mapper leaves the {@code id} value un-prefixed and values un-split.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class DumDbRowMapper {

    private static final long MEGA_BYTE = 1024L * 1024L;
    private static final String SQL_TIMESTAMP_CLASS = "java.sql.Timestamp";
    private static final String INTEGER_CLASS = "java.lang.Integer";
    private static final String UTC = "UTC";
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    /** Resolve the configured custom-class hook, or {@code null} when none is set. */
    public DumDbExtCustomImpl instantiateCustomClass(String className) {
        return Optional.ofNullable(className)
                .map(cn -> (DumDbExtCustomImpl) DumCustomClassCache.getCustomClassMap(cn).orElse(null))
                .orElse(null);
    }

    /**
     * Build the attribute map for the row the result set is currently positioned on: every column
     * keyed by its label, plus any Tika file content and custom-class contributions. Values are raw
     * (scalar) — the caller projects them onto the declared schema and splits multi-valued fields.
     */
    public Map<String, Object> mapRow(DumDbSource source, ResultSet rs, Connection conn,
            DumDbExtCustomImpl customImpl) throws SQLException {
        Map<String, Object> attributes = new LinkedHashMap<>();
        addColumns(source, rs, attributes);
        addFileAttributes(source, attributes);
        return applyCustomClass(customImpl, source.getCustomClassName(), conn, attributes);
    }

    private void addColumns(DumDbSource source, ResultSet rs, Map<String, Object> attributes)
            throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        String htmlFields = source.getRemoveHtmlTagsFields() != null
                ? source.getRemoveHtmlTagsFields()
                : "";
        for (int c = 1; c <= metaData.getColumnCount(); c++) {
            String columnName = metaData.getColumnLabel(c);
            String className = metaData.getColumnClassName(c);
            if (INTEGER_CLASS.equals(className)) {
                attributes.put(columnName,
                        formatValue(columnName, Integer.toString(rs.getInt(c)), htmlFields));
            } else if (SQL_TIMESTAMP_CLASS.equals(className)) {
                DateFormat df = new SimpleDateFormat(DATE_FORMAT);
                df.setTimeZone(TimeZone.getTimeZone(UTC));
                attributes.put(columnName, formatValue(columnName, df.format(rs.getDate(c)), htmlFields));
            } else {
                attributes.put(columnName, formatValue(columnName, rs.getString(c), htmlFields));
            }
        }
    }

    private String formatValue(String fieldName, String value, String htmlFields) {
        if (value == null) {
            return null;
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
        if (filePathField == null || !attributes.containsKey(filePathField)) {
            return;
        }
        DumTikaFileAttributes tikaAttrs = DumFileUtils.readFile((String) attributes.get(filePathField));
        if (tikaAttrs == null) {
            return;
        }
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

    private Map<String, Object> applyCustomClass(DumDbExtCustomImpl customImpl, String className,
            Connection conn, Map<String, Object> attributes) {
        if (className != null && customImpl != null) {
            return new HashMap<>(customImpl.run(conn, attributes));
        }
        return attributes;
    }
}
