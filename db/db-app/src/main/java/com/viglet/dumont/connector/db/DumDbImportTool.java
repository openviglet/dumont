/*
 * Copyright (C) 2016-2022 the original author or authors.
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
package com.viglet.dumont.connector.db;

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
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.viglet.dumont.commons.cache.DumCustomClassCache;
import com.viglet.dumont.connector.db.ext.DumDbExtCustomImpl;
import com.viglet.dumont.connector.db.format.DumDbFormatValue;
import com.viglet.dumont.connector.filesystem.commons.DumFileUtils;
import com.viglet.dumont.connector.filesystem.commons.DumTikaFileAttributes;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;

import ch.qos.logback.classic.Level;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Class that can be used to bootstrap and launch JDBC Import Tool
 *
 * @author Alexandre Oliveira
 * @since 0.3.0
 **/

@Getter
@Setter
@Slf4j
public class DumDbImportTool {
    private static final long MEGA_BYTE = 1024L * 1024L;
    private static final String PROVIDER_ATTRIBUTE_VALUE = "dumont-jdbc";
    private static final String SQL_TIMESTAMP_CLASS = "java.sql.Timestamp";
    private static final String INTEGER_CLASS = "java.lang.Integer";
    private static final String UTC = "UTC";
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String FILE_PROTOCOL = "file://";
    @Parameter(names = { "--deindex-before-importing" }, description = "Deindex before importing", arity = 1)
    private boolean deindexBeforeImporting = false;

    @Parameter(names = { "--max-content-size" }, description = "Maximum size that content can be indexed (megabytes)")
    private long maxContentMegaByteSize = 5;
    @Parameter(names = "--debug", description = "Change the log level to debug", help = true)
    private boolean debug = false;
    @Parameter(names = { "--driver", "-d" }, description = "Manually specify JDBC driver class to use", required = true)
    private String driver = null;

    @Parameter(names = { "--connect", "-c" }, description = "Specify JDBC connect string", required = true)
    private String connect = null;

    @Parameter(names = { "--query",
            "-q" }, description = "Import the results of statement, if it starts with file://, it will read the file with statement", required = true)
    private String query = null;

    @Parameter(names = { "--site" }, description = "Specify the Semantic Navigation Site", required = true)
    private String site = null;

    @Parameter(names = { "--locale" }, description = "Specify the Semantic Navigation Site Locale")
    private String locale = "en_US";

    @Parameter(names = { "--server", "-s" }, description = "Viglet Dumont Server")
    private String dumontServer = "http://localhost:2700";

    @Parameter(names = { "--api-key", "-a" }, description = "Set Dumont API Key")
    private String dumApiKey = null;

    @Parameter(names = { "--db-username" }, description = "Set authentication database username")
    private String dbUsername = null;

    @Parameter(names = { "--db-password" }, description = "Set authentication database password")
    private String dbPassword = null;

    @Parameter(names = { "--type", "-t" }, description = "Set Content Type name")
    public String type = "CONTENT_TYPE";

    @Parameter(names = { "--chunk", "-z" }, description = "Number of items to be sent to the queue")
    private int chunk = 100;

    @Parameter(names = { "--include-type-in-id", "-i" }, description = "Include Content Type name in Id", arity = 1)
    private boolean typeInId = false;

    @Parameter(names = { "--multi-valued-separator" }, description = "Multi Valued Separator")
    private String mvSeparator = ",";

    @Parameter(names = { "--multi-valued-field" }, description = "Multi Valued Fields")
    private String mvField = "";

    @Parameter(names = { "--remove-html-tags-field" }, description = "Remove HTML Tags into content of field")
    private String htmlField = "";

    @Parameter(names = "--file-path-field", description = "Field with File Path", help = true)
    private String filePathField = null;

    @Parameter(names = "--file-content-field", description = "Field that shows Content of File", help = true)
    private String fileContentField = null;

    @Parameter(names = "--file-size-field", description = "Field that shows Size of File in bytes", help = true)
    private String fileSizeField = null;

    @Parameter(names = "--class-name", description = "Customized Class to modified rows", help = true)
    private String customClassName = null;

    @Parameter(names = { "--show-output", "-o" }, description = "Show Output", arity = 1)
    private boolean showOutput = false;

    @Parameter(names = { "--encoding" }, description = "Encoding Source")
    private String encoding = "UTF-8";

    @Parameter(names = "--help", description = "Print usage instructions", help = true)
    private boolean help = false;

    private static DumDbFormatValue dumFormatValue = null;

    public String getFormattedQuery() {
        try {
            return query.startsWith(FILE_PROTOCOL) ? Files.readString(Paths.get(query.replace(FILE_PROTOCOL, "")))
                    : query;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return query;
    }

    public static void main(String... argv) {

        DumDbImportTool main = new DumDbImportTool();
        JCommander jCommander = JCommander.newBuilder().addObject(main).build();
        jCommander.getConsole().println("Viglet Dumont JDBC Import Tool. " +
                DumDbImportTool.class.getPackage().getImplementationVersion());
        try {
            jCommander.parse(argv);
            if (main.debug) {
                ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
                        .setLevel(Level.DEBUG);
            }
            if (main.help) {
                jCommander.usage();
                return;
            }
            dumFormatValue = new DumDbFormatValue(main);
            main.run();
        } catch (ParameterException e) {
            log.info("Error: {}", e.getLocalizedMessage());
            jCommander.usage();
        }

    }

    public void run() {
        log.info("driver: {}", driver);
        log.info("connect: {}", connect);
        log.info("query: {}", getFormattedQuery());
        log.info("username: {}", dbUsername);

        this.select();
    }

    private void select() {
        if (loadJDBCDriver()) {
            TurSNServer dumSNServer = createTurSNServer();
            DumDbExtCustomImpl dumJDBCCustomImpl = instantiateCustomClass();
            if (this.deindexBeforeImporting) {
                dumSNServer.deleteItemsByType(type);
            }
            log.info("Execute a query...");
            try (Connection conn = DriverManager.getConnection(connect, dbUsername, dbPassword);
                    Statement stmt = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                            ResultSet.CONCUR_READ_ONLY);
                    ResultSet rs = stmt.executeQuery(getFormattedQuery())) {
                int totalRows = 0;
                if (rs.last()) {
                    totalRows = rs.getRow();
                    // Move to beginning
                    rs.beforeFirst();
                }

                DumDbChunkingJob dumChunkingJob = new DumDbChunkingJob(chunk);
                while (rs.next()) {
                    dumChunkingJob.addItem(createJobItem(dumJDBCCustomImpl, conn, rs));
                    if (dumChunkingJob.isChunkLimit()) {
                        this.sendServer(dumSNServer, dumChunkingJob, totalRows);
                        dumChunkingJob.newCicle();
                    }
                }
                if (dumChunkingJob.hasItemsLeft()) {
                    this.sendServer(dumSNServer, dumChunkingJob, totalRows);
                }
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    private boolean loadJDBCDriver() {
        try {
            Class.forName(driver);
            return true;
        } catch (ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    private TurSNServer createTurSNServer() {
        TurSNServer dumSNServer = new TurSNServer(URI.create(dumontServer),
                this.site,
                LocaleUtils.toLocale(this.locale),
                new TurApiKeyCredentials(dumApiKey));

        dumSNServer.setProviderName(PROVIDER_ATTRIBUTE_VALUE);
        return dumSNServer;
    }

    private DumDbExtCustomImpl instantiateCustomClass() {
        return Optional.ofNullable(customClassName)
                .map(className -> (DumDbExtCustomImpl) DumCustomClassCache.getCustomClassMap(customClassName)
                        .orElse(null))
                .orElse(null);

    }

    private TurSNJobItem createJobItem(DumDbExtCustomImpl dumJDBCCustomImpl, Connection conn, ResultSet rs)
            throws SQLException {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("type", type);
        attributes.put("sourceApps", PROVIDER_ATTRIBUTE_VALUE);
        addDBFieldsAsAttributes(rs, attributes);
        addFileAttributes(attributes);
        attributes = modifyAttributesByCustomClass(dumJDBCCustomImpl, conn, attributes);
        addMultiValuedAttributes(attributes);
        TurSNJobItem turSNJobItem = new TurSNJobItem(TurSNJobAction.CREATE, Collections.singletonList(this.site),
                LocaleUtils.toLocale(locale));
        turSNJobItem.setAttributes(attributes);

        return turSNJobItem;
    }

    private Map<String, Object> modifyAttributesByCustomClass(DumDbExtCustomImpl dumJDBCCustomImpl, Connection conn,
            Map<String, Object> attributes) {
        return (hasCustomClass(dumJDBCCustomImpl)) ? dumJDBCCustomImpl.run(conn, attributes) : attributes;
    }

    private boolean hasCustomClass(DumDbExtCustomImpl dumJDBCCustomImpl) {
        return customClassName != null && dumJDBCCustomImpl != null;
    }

    private void addDBFieldsAsAttributes(ResultSet rs, Map<String, Object> attributes) throws SQLException {
        ResultSetMetaData resultSetMetaData = rs.getMetaData();
        for (int c = 1; c <= resultSetMetaData.getColumnCount(); c++) {
            String nameSensitive = resultSetMetaData.getColumnLabel(c);
            String className = resultSetMetaData.getColumnClassName(c);
            if (className.equals(INTEGER_CLASS)) {
                int intValue = rs.getInt(c);
                attributes.put(nameSensitive, dumFormatValue.format(nameSensitive, Integer.toString(intValue)));
            } else if (className.equals(SQL_TIMESTAMP_CLASS)) {
                DateFormat df = new SimpleDateFormat(DATE_FORMAT);
                df.setTimeZone(TimeZone.getTimeZone(UTC));
                attributes.put(nameSensitive, dumFormatValue.format(nameSensitive, df.format(rs.getDate(c))));
            } else {
                String strValue = rs.getString(c);
                attributes.put(nameSensitive, dumFormatValue.format(nameSensitive, strValue));
            }
        }
    }

    private void addFileAttributes(Map<String, Object> attributes) {
        if (filePathField != null && attributes.containsKey(filePathField)) {
            DumTikaFileAttributes dumTikaFileAttributes = DumFileUtils.readFile((String) attributes.get(filePathField));
            if (dumTikaFileAttributes != null) {
                addFileSizeAttribute(attributes, dumTikaFileAttributes);
                addFileContentAttribute(attributes, dumTikaFileAttributes);
            }
        }
    }

    private void addFileContentAttribute(Map<String, Object> attributes, DumTikaFileAttributes dumTikaFileAttributes) {
        if (fileContentField != null) {
            long maxContentByteSize = maxContentMegaByteSize * MEGA_BYTE;

            if (dumTikaFileAttributes.getContent().getBytes().length <= maxContentByteSize) {
                attributes.put(fileContentField, dumTikaFileAttributes.getContent());
            } else {
                attributes.put(fileContentField,
                        dumTikaFileAttributes.getContent().substring(0, Math.toIntExact(maxContentByteSize)));
                if (log.isDebugEnabled()) {
                    log.debug("File size greater than {}, truncating content ...:",
                            FileUtils.byteCountToDisplaySize(maxContentByteSize));
                }
            }
        } else {
            log.debug("File without content: {}", filePathField);
        }
    }

    private void addFileSizeAttribute(Map<String, Object> attributes, DumTikaFileAttributes dumTikaFileAttributes) {
        if (fileSizeField != null && dumTikaFileAttributes.getFile() != null) {
            attributes.put(fileSizeField, dumTikaFileAttributes.getFile().length());
            if (log.isDebugEnabled()) {
                log.debug("File: {}", dumTikaFileAttributes.getFile().getAbsolutePath());
                log.debug("File size: {}", FileUtils.byteCountToDisplaySize(dumTikaFileAttributes.getFile().length()));
                log.debug("File - Content size: {}",
                        FileUtils.byteCountToDisplaySize(dumTikaFileAttributes.getContent().getBytes().length));
            }
        } else {
            log.debug("File without size: {}", filePathField);
        }
    }

    private void addMultiValuedAttributes(Map<String, Object> attributes) {
        attributes.forEach((attributeName, value) -> {
            String attributeValue = String.valueOf(value);
            addOnlyMultiValuedAttributes(attributes, attributeName, attributeValue);
        });
    }

    private void addOnlyMultiValuedAttributes(Map<String, Object> attributes, String attributeName,
            String attributeValue) {
        Arrays.stream(mvField.toLowerCase().split(","))
                .filter(strMvField -> attributeName.equalsIgnoreCase(strMvField.toLowerCase())
                        && attributeValue != null)
                .map(strMvField -> Arrays.asList(attributeValue.split(mvSeparator)))
                .forEach(multiValueList -> attributes.put(attributeName, multiValueList));
    }

    private void sendServer(TurSNServer dumSNServer, DumDbChunkingJob dumChunkingJob, int totalRows) {
        log.info("Importing {} to {} of {} items", dumChunkingJob.getFirstItemPosition(),
                dumChunkingJob.getTotal(), totalRows);
        dumSNServer.importItems(dumChunkingJob.getTurSNJobItems(), showOutput);
    }
}
