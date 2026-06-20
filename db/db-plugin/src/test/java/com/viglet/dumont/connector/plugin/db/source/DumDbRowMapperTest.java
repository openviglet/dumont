/*
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

package com.viglet.dumont.connector.plugin.db.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.viglet.dumont.connector.db.ext.DumDbExtCustomImpl;
import com.viglet.dumont.connector.plugin.db.persistence.model.DumDbSource;

/**
 * Covers {@link DumDbRowMapper}: per-column JDBC type mapping, HTML stripping, Tika file content
 * enrichment and the custom-class hook, all over a mocked {@link ResultSet}.
 */
class DumDbRowMapperTest {

    private final DumDbRowMapper mapper = new DumDbRowMapper();

    private static ResultSetMetaData metaData(String[] labels, String[] classNames)
            throws Exception {
        ResultSetMetaData md = mock(ResultSetMetaData.class);
        when(md.getColumnCount()).thenReturn(labels.length);
        for (int i = 0; i < labels.length; i++) {
            lenient().when(md.getColumnLabel(i + 1)).thenReturn(labels[i]);
            lenient().when(md.getColumnClassName(i + 1)).thenReturn(classNames[i]);
        }
        return md;
    }

    @Test
    void mapRowMapsColumnsByJdbcType() throws Exception {
        ResultSetMetaData md = metaData(
                new String[] {"id", "created", "title"},
                new String[] {"java.lang.Integer", "java.sql.Timestamp", "java.lang.String"});
        ResultSet rs = mock(ResultSet.class);
        when(rs.getMetaData()).thenReturn(md);
        when(rs.getInt(1)).thenReturn(7);
        when(rs.getDate(2)).thenReturn(new java.sql.Date(0L));
        when(rs.getString(3)).thenReturn("Hello");
        DumDbSource source = DumDbSource.builder().build();

        Map<String, Object> attrs = mapper.mapRow(source, rs, null, null);

        assertThat(attrs).containsEntry("id", "7").containsEntry("title", "Hello");
        assertThat(attrs.get("created").toString()).contains("1970-01-01");
    }

    @Test
    void mapRowStripsHtmlForConfiguredFields() throws Exception {
        ResultSetMetaData md = metaData(new String[] {"body"}, new String[] {"java.lang.String"});
        ResultSet rs = mock(ResultSet.class);
        when(rs.getMetaData()).thenReturn(md);
        when(rs.getString(1)).thenReturn("<p>Hi <b>there</b></p>");
        DumDbSource source = DumDbSource.builder().removeHtmlTagsFields("body").build();

        Map<String, Object> attrs = mapper.mapRow(source, rs, null, null);

        assertThat(attrs).containsEntry("body", "Hi there");
    }

    @Test
    void mapRowAppliesCustomClassWhenConfigured() throws Exception {
        ResultSetMetaData md = metaData(new String[] {"a"}, new String[] {"java.lang.String"});
        ResultSet rs = mock(ResultSet.class);
        when(rs.getMetaData()).thenReturn(md);
        when(rs.getString(1)).thenReturn("x");
        DumDbSource source = DumDbSource.builder().customClassName("com.x.Custom").build();
        Connection conn = mock(Connection.class);
        DumDbExtCustomImpl customImpl = mock(DumDbExtCustomImpl.class);
        when(customImpl.run(eq(conn), any())).thenReturn(Map.of("k", "v"));

        Map<String, Object> attrs = mapper.mapRow(source, rs, conn, customImpl);

        assertThat(attrs).containsEntry("k", "v");
    }

    @Test
    void instantiateCustomClassNullWhenNoClassName() {
        assertThat(mapper.instantiateCustomClass(null)).isNull();
    }

    @Test
    void mapRowEnrichesWithTikaFileContent(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("doc.txt");
        Files.writeString(file, "Indexed file body", StandardCharsets.UTF_8);
        ResultSetMetaData md = metaData(new String[] {"path"}, new String[] {"java.lang.String"});
        ResultSet rs = mock(ResultSet.class);
        when(rs.getMetaData()).thenReturn(md);
        when(rs.getString(1)).thenReturn(file.toAbsolutePath().toString());
        DumDbSource source = DumDbSource.builder().filePathField("path")
                .fileContentField("content").fileSizeField("size").maxContentMegaByteSize(10).build();

        Map<String, Object> attrs = mapper.mapRow(source, rs, null, null);

        assertThat(attrs.get("content").toString()).contains("Indexed file body");
        assertThat(attrs).containsKey("size");
    }
}
