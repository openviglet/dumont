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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance;
import com.viglet.dumont.connector.db.ext.DumDbExtCustomImpl;
import com.viglet.dumont.connector.plugin.db.DumDbPlugin;
import com.viglet.dumont.connector.plugin.db.persistence.model.DumDbField;
import com.viglet.dumont.connector.plugin.db.persistence.model.DumDbSource;
import com.viglet.turing.commons.se.field.TurSEFieldType;

import lombok.extern.slf4j.Slf4j;

/**
 * A {@link DumStructuredSource} that adapts a {@link DumDbSource}'s per-row mapping to the structured
 * SPI (roadmap <strong>D20</strong>, DB pilot).
 *
 * <p>
 * Instead of the legacy all-rows batch loop, this honours the structured contract: {@link #discover()}
 * runs an id-list query ({@code SELECT <pk> FROM (<query>) sub}) and {@link #extract(String)} fetches a
 * single row ({@code … WHERE <pk> = ?}) and maps it onto the declared {@link #schema()}. Grounding,
 * dry-run preview (D14) and field-coverage/drift (D15) are inherited from
 * {@link com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourcePlugin} — none of it is
 * reimplemented here. The user's query is reused verbatim as a derived table, so the indexed rows match
 * the legacy path exactly.
 *
 * <p>
 * The {@code id} space is the <em>raw</em> primary-key value: {@code discover()} returns it and
 * {@code extract(id)} binds it in the {@code WHERE}. The published document id applies the source's
 * {@code typeInId} prefix, matching the legacy document identity.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public class DumDbStructuredSource implements DumStructuredSource {

    private static final String FILE_PROTOCOL = "file://";
    private static final String DEFAULT_PRIMARY_KEY = "id";
    private static final String DEFAULT_CONTENT_TYPE = "CONTENT_TYPE";
    private static final String DEFAULT_SEPARATOR = ",";

    private final DumDbSource source;
    private final DumDbRowMapper rowMapper;

    private DumStructuredSchema schema;
    private DumDbExtCustomImpl customImpl;
    private boolean customImplResolved;

    public DumDbStructuredSource(DumDbSource source, DumDbRowMapper rowMapper) {
        this.source = source;
        this.rowMapper = rowMapper;
    }

    @Override
    public String getSourceName() {
        return source.getName();
    }

    @Override
    public String getProviderName() {
        return DumDbPlugin.PROVIDER_NAME;
    }

    @Override
    public Collection<String> getSiteNames() {
        return source.getTurSNSites();
    }

    /** The Turing content type published as the document {@code type}; defaults to {@code CONTENT_TYPE}. */
    public String getContentType() {
        return source.getContentType() != null ? source.getContentType() : DEFAULT_CONTENT_TYPE;
    }

    @Override
    public Locale getLocale() {
        String locale = source.getLocale();
        if (locale == null || locale.isBlank()) {
            return Locale.US;
        }
        String[] parts = locale.split("[_-]", 2);
        return parts.length == 2 ? Locale.of(parts[0], parts[1]) : Locale.of(parts[0]);
    }

    @Override
    public synchronized DumStructuredSchema schema() {
        if (schema == null) {
            schema = source.getFields() == null || source.getFields().isEmpty()
                    ? deriveSchemaFromQuery()
                    : declaredSchema();
        }
        return schema;
    }

    @Override
    public List<String> discover() {
        String sql = "SELECT sub." + primaryKeyColumn() + " FROM (" + query() + ") sub";
        List<String> ids = new ArrayList<>();
        try (Connection conn = openConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String id = rs.getString(1);
                if (id != null && !id.isBlank()) {
                    ids.add(id);
                }
            }
        } catch (SQLException e) {
            log.error("DB source '{}' failed to discover ids: {}", source.getName(), e.getMessage(), e);
        }
        log.info("DB source '{}' discovered {} id(s)", source.getName(), ids.size());
        return ids;
    }

    @Override
    public Optional<DumStructuredRecord> extract(String contentId) {
        String sql = "SELECT sub.* FROM (" + query() + ") sub WHERE sub." + primaryKeyColumn() + " = ?";
        try (Connection conn = openConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            bindKey(ps, contentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Map<String, Object> attributes = rowMapper.mapRow(source, rs, conn, customImpl());
                return Optional.of(toRecord(contentId, attributes));
            }
        } catch (SQLException e) {
            log.error("DB source '{}' failed to extract id '{}': {}", source.getName(), contentId,
                    e.getMessage(), e);
            return Optional.empty();
        }
    }

    /** Build a schema-bound record from the raw row attributes, projecting onto the declared fields. */
    private DumStructuredRecord toRecord(String contentId, Map<String, Object> attributes) {
        String pk = primaryKeyColumn();
        // Column labels are matched case-insensitively: databases differ on identifier case
        // (H2/Oracle fold to upper, PostgreSQL to lower), but the declared field name is authoritative.
        Map<String, Object> byLowerKey = new HashMap<>();
        attributes.forEach((key, value) -> byLowerKey.put(key.toLowerCase(Locale.ROOT), value));
        DumStructuredRecord.Builder rec = DumStructuredRecord.builder(documentId(contentId), schema());
        for (DumStructuredField field : schema().fields()) {
            // The primary key is the document identity, not a content field.
            if (field.name().equalsIgnoreCase(pk)) {
                continue;
            }
            Object value = byLowerKey.get(field.name().toLowerCase(Locale.ROOT));
            if (value == null) {
                continue;
            }
            Object grounded = field.multiValued() && value instanceof String text
                    ? splitMultiValued(text)
                    : value;
            try {
                rec.set(field.name(), grounded, DumFieldProvenance.tabular(field.name()));
            } catch (IllegalArgumentException e) {
                log.warn("DB source '{}' id '{}' field '{}' skipped: {}", source.getName(), contentId,
                        field.name(), e.getMessage());
            }
        }
        return rec.build();
    }

    /** Build the declared schema from the source's column&rarr;field declarations. */
    private DumStructuredSchema declaredSchema() {
        DumStructuredSchema.Builder builder = DumStructuredSchema.builder();
        for (DumDbField field : source.getFields()) {
            if (field.getName() == null || field.getName().isBlank()) {
                continue;
            }
            builder.field(DumStructuredField.builder(field.getName())
                    .type(field.getType() != null ? field.getType() : TurSEFieldType.STRING)
                    .multiValued(field.isMultiValued())
                    .facet(field.isFacet())
                    .mandatory(field.isMandatory())
                    .description(field.getDescription())
                    .embeddingRole(field.getEmbeddingRole())
                    .build());
        }
        return builder.build();
    }

    /**
     * Derive a STRING schema from the query's result-set metadata when no fields are declared, so a
     * legacy source (every column indexed as text) keeps working at parity. The primary key column is
     * the document id and is omitted; columns named in {@code multiValuedFields} are marked
     * multi-valued.
     */
    private DumStructuredSchema deriveSchemaFromQuery() {
        String sql = "SELECT sub.* FROM (" + query() + ") sub WHERE 1 = 0";
        String pk = primaryKeyColumn();
        List<String> multiValued = multiValuedFieldNames();
        DumStructuredSchema.Builder builder = DumStructuredSchema.builder();
        try (Connection conn = openConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData metaData = rs.getMetaData();
            for (int c = 1; c <= metaData.getColumnCount(); c++) {
                String column = metaData.getColumnLabel(c);
                if (column.equalsIgnoreCase(pk)) {
                    continue;
                }
                builder.field(DumStructuredField.builder(column).type(TurSEFieldType.STRING)
                        .multiValued(multiValued.contains(column.toLowerCase())).build());
            }
        } catch (SQLException e) {
            log.error("DB source '{}' failed to derive schema: {}", source.getName(), e.getMessage(), e);
        }
        return builder.build();
    }

    private List<String> multiValuedFieldNames() {
        if (source.getMultiValuedFields() == null || source.getMultiValuedFields().isBlank()) {
            return List.of();
        }
        return Arrays.stream(source.getMultiValuedFields().toLowerCase().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private List<Object> splitMultiValued(String value) {
        String separator = source.getMultiValuedSeparator() != null
                && !source.getMultiValuedSeparator().isEmpty()
                        ? source.getMultiValuedSeparator()
                        : DEFAULT_SEPARATOR;
        List<Object> values = new ArrayList<>();
        for (String part : value.split(java.util.regex.Pattern.quote(separator))) {
            if (!part.isBlank()) {
                values.add(part.trim());
            }
        }
        return values;
    }

    private String documentId(String rawId) {
        return source.isTypeInId() ? getContentType() + "_" + rawId : rawId;
    }

    private String primaryKeyColumn() {
        String pk = source.getPrimaryKeyColumn();
        return pk != null && !pk.isBlank() ? pk.trim() : DEFAULT_PRIMARY_KEY;
    }

    /** Bind the key as a number when it looks numeric (so strict-typed columns match), else as text. */
    private static void bindKey(PreparedStatement ps, String contentId) throws SQLException {
        try {
            ps.setLong(1, Long.parseLong(contentId.trim()));
        } catch (NumberFormatException e) {
            ps.setString(1, contentId);
        }
    }

    private synchronized DumDbExtCustomImpl customImpl() {
        if (!customImplResolved) {
            customImpl = rowMapper.instantiateCustomClass(source.getCustomClassName());
            customImplResolved = true;
        }
        return customImpl;
    }

    private Connection openConnection() throws SQLException {
        loadDriver(source.getDriver());
        return DriverManager.getConnection(source.getUrl(), source.getDbUsername(),
                source.getDbPassword());
    }

    private static void loadDriver(String driver) throws SQLException {
        if (driver == null || driver.isBlank()) {
            return;
        }
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC driver not found: " + driver, e);
        }
    }

    /** The source query, resolving a {@code file://} reference and stripping a trailing {@code ;}. */
    private String query() {
        String query = source.getQuery();
        if (query != null && query.startsWith(FILE_PROTOCOL)) {
            try {
                query = Files.readString(Paths.get(query.replace(FILE_PROTOCOL, "")));
            } catch (IOException e) {
                log.error("DB source '{}' failed to read query file: {}", source.getName(),
                        e.getMessage(), e);
                return query;
            }
        }
        if (query == null) {
            return "";
        }
        String trimmed = query.strip();
        return trimmed.endsWith(";") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
