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

package com.viglet.dumont.connector.filesystem;

import static com.viglet.turing.client.sn.TurSNConstants.ID_ATTR;

import java.io.File;
import java.io.IOException;
import java.net.URI;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.LocaleUtils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.viglet.turing.client.auth.TurServer;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.ocr.TurOcr;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DumFSImportTool {
    @Parameter(names = "--source-dir", description = "Source Directory that contains files", required = true)
    private String sourceDir = null;

    @Parameter(names = "--prefix-from-replace", description = "Prefix from Replace")
    private String prefixFromReplace = null;

    @Parameter(names = "--prefix-to-replace", description = "Prefix to Replace")
    private String prefixToReplace = null;

    @Parameter(names = { "--server", "-s" }, description = "Viglet Dumont Server", required = true)
    private String dumontServer = "http://localhost:2700";

    @Parameter(names = { "--api-key", "-a" }, description = "Viglet Dumont API Key", required = true)
    private String dumontApiKey = null;

    @Parameter(names = { "--site" }, description = "Specify the Semantic Navigation Site", required = true)
    private String site = null;

    @Parameter(names = { "--locale" }, description = "Specify the Semantic Navigation Site Locale")
    private String locale = "en_US";

    @Parameter(names = { "--type", "-t" }, description = "Set Content Type name")
    public String type = "Static File";

    @Parameter(names = { "--chunk", "-z" }, description = "Number of items to be sent to the queue")
    private int chunk = 100;

    @Parameter(names = { "--include-type-in-id", "-i" }, description = "Include Content Type name in Id", arity = 1)
    public boolean typeInId = false;

    @Parameter(names = "--file-size-field", description = "Field that shows Size of File in bytes", help = true)
    private String fileSizeField = "fileSize";

    @Parameter(names = "--file-extension-field", description = "Field that shows extension of File", help = true)
    private String fileExtensionField = "fileExtension";

    @Parameter(names = { "--show-output", "-o" }, description = "Show Output", arity = 1)
    public boolean showOutput = false;

    @Parameter(names = { "--encoding" }, description = "Encoding Source")
    public String encoding = "UTF-8";

    @Parameter(names = "--help", description = "Print usage instructions", help = true)
    private boolean help = false;

    public static void main(String... argv) {
        DumFSImportTool main = new DumFSImportTool();
        JCommander jCommander = JCommander.newBuilder().addObject(main).build();
        jCommander.getConsole().println("Viglet Dumont Filesystem Import Tool. " +
                DumFSImportTool.class.getPackage().getImplementationVersion());
        try {
            jCommander.parse(argv);
            if (main.help) {
                jCommander.usage();
                return;
            }
            log.info("Viglet Dumont Filesystem Import Tool.");
            main.run();
        } catch (ParameterException e) {
            // Handle everything on your own, i.e.
            log.info("Error: {}", e.getLocalizedMessage());
            jCommander.usage();
        }

    }

    public void run() {
        DumFSChunkingJob dumChunkingJob = new DumFSChunkingJob(chunk);
        Path startPath = Paths.get(sourceDir);
        long totalFiles = Objects.requireNonNull(startPath.toFile().list()).length;
        try {
            Files.walkFileTree(startPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                            File file = new File(path.toAbsolutePath().toString());
                            log.info("Process file: {}", file.getAbsolutePath());

                            dumChunkingJob.addItem(createJobItem(file));
                            if (dumChunkingJob.isChunkLimit()) {
                                sendToDumont(dumChunkingJob, totalFiles);
                                dumChunkingJob.newCicle();
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
            if (dumChunkingJob.hasItemsLeft()) {
                this.sendToDumont(dumChunkingJob, totalFiles);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

    }

    private TurSNJobItem createJobItem(File file) {
        TurSNJobItem turSNJobItem = new TurSNJobItem(TurSNJobAction.CREATE, Collections.singletonList(this.site),
                LocaleUtils.toLocale(locale));
        Map<String, Object> attributes = new HashMap<>();

        List<String> webImagesExtensions = new ArrayList<>(
                Arrays.asList("pnm", "png", "jpg", "jpeg", "gif"));
        String extension = FilenameUtils.getExtension(file.getAbsolutePath()).toLowerCase();
        if (!extension.equals("ds_store")) {

            TimeZone tz = TimeZone.getTimeZone("UTC");
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            df.setTimeZone(tz);
            String fileURL = file.getAbsolutePath();
            if (prefixFromReplace != null && prefixToReplace != null)
                fileURL = fileURL.replace(prefixFromReplace, prefixToReplace);
            if (typeInId)
                attributes.put("id", type + fileURL);
            else
                attributes.put("id", fileURL);
            attributes.put("date", df.format(file.lastModified()));
            attributes.put("title", file.getName());
            attributes.put("type", type);
            if (webImagesExtensions.contains(extension))
                attributes.put("image", fileURL);
            if (fileExtensionField != null)
                attributes.put(fileExtensionField, extension);
            if (fileSizeField != null)
                attributes.put(fileSizeField, file.length());
            attributes.put("url", fileURL);
            TurOcr ocr = new TurOcr();
            attributes.put("text", ocr.processFile(new TurServer(URI.create(dumontServer),
                    new TurApiKeyCredentials(dumontApiKey)), file, showOutput));
            turSNJobItem.setAttributes(attributes);

            return turSNJobItem;
        }
        return null;
    }

    private void sendToDumont(DumFSChunkingJob dumChunkingJob, long totalFiles) {
        log.info("Send job to Dumont");
        if (log.isDebugEnabled()) {
            for (TurSNJobItem turSNJobItem : dumChunkingJob.getTurSNJobItems()) {
                log.debug("TurSNJobItem Id: {}", turSNJobItem.getAttributes().get(ID_ATTR));
            }
        }
        log.info("Importing {} to {} of {} items", dumChunkingJob.getFirstItemPosition(),
                dumChunkingJob.getTotal(), totalFiles);
        TurSNJobUtils.importItems(dumChunkingJob.getTurSNJobItems(),
                new TurSNServer(URI.create(dumontServer), null,
                        new TurApiKeyCredentials(dumontApiKey)),
                showOutput);

    }
}
